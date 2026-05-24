package fr.inra.oresing.persistence;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.AuthenticationServiceImpl;
import fr.inra.oresing.domain.authorization.CurrentUserRolesResult;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdminException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.exceptions.AuthenticationFailure;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.port.AuthenticationPort;
import fr.inra.oresing.domain.repository.authorization.role.*;
import fr.inra.oresing.domain.user.CreateUserRequest;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.rest.CreateUserResult;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.model.authorization.UserAuthorizationForApplication;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class AuthenticationService implements AuthenticationServiceImpl, AuthenticationPort {
    @Setter
    private ServiceContainer serviceContainer;

    private final UserRepository userRepository;

    private final SqlService db;

    @Value("${bcryptCost:12}")
    private int bcryptCost;

    /**
     * Duree de validite d'une cle de validation generee par mail
     * ( activation compte , mot de passe oublie , changement d'email ) .
     * Au-dela , la cle est rejetee meme bien formee ; l'utilisateur doit
     * relancer le flow . Configurable via env OPENADOM_AUTH_VALIDATION_KEY_TTL_MINUTES
     * ( cf application.properties pour la doc complete ) .
     */
    @Value("${openadom.auth.validation-key-ttl-minutes:10}")
    private int validationKeyTtlMinutes;

    public AuthenticationService(
            UserRepository userRepository,
            SqlService db,
            ServiceContainer serviceContainer) {
        this.userRepository = userRepository;
        this.db = db;
        this.serviceContainer = serviceContainer;
    }

    /**
     * Construit la cle de verification envoyee par mail ( activation compte,
     * changement d'email, mot de passe oublie ).
     *
     * <p>Reste deterministe a partir des proprietes utilisateur ( id, email,
     * hash bcrypt du mot de passe, date de creation ) pour permettre la
     * verification cote serveur sans persistance dediee. La fonction est
     * volontairement basee sur SHA-256 pour fermer la breche connue ou
     * {@code String.hashCode()} sur 32 bits ne fournissait que ~10^9 cles
     * possibles, sur lesquelles {@code Math.abs} introduisait en plus le
     * cas pathologique {@code Integer.MIN_VALUE -> MIN_VALUE} ( cle au
     * format negatif ).</p>
     *
     * <p>Sortie : 12 chiffres hexadecimaux ( 48 bits d'entropie cryptographique ),
     * imprevisible sans connaitre le hash bcrypt opaque du mot de passe.
     * Le format reste compatible avec le frontend qui n'attend qu'une chaine
     * suffisamment courte pour etre saisie a la main.</p>
     */
    private static String generateVerificationKey(final OreSiUser oreSiUser) {
        return generateVerificationKey(oreSiUser, oreSiUser.getEmail());
    }

    /**
     * Variante qui derive la cle a partir d'un email cible explicite plutot
     * que de {@code user.email} . Indispensable pour le flow email-change
     * 2-phases : a la phase 1 , user.email pointe encore sur l'ancien email
     * ( decouple ; cf {@code pending_email} ) ; la cle envoyee par mail doit
     * cependant rester valide apres le swap en phase 2 ( ou user.email
     * devient pendingEmail ) . En la binant a pendingEmail des le depart
     * on garantit que la meme cle est verifiable AVANT et APRES le swap .
     *
     * <p>Effet secondaire securite : la cle est implicitement liee a la
     * cible ; un attaquant qui interceptererait la cle ne pourrait pas
     * detourner le changement vers un autre email ( la cle serait invalide
     * pour toute autre cible ) .
     */
    private static String generateVerificationKey(final OreSiUser oreSiUser, final String targetEmail) {
        Objects.requireNonNull(oreSiUser, "oreSiUser");
        final String input = String.join("|",
                String.valueOf(oreSiUser.getId()),
                Objects.toString(targetEmail, ""),
                Objects.toString(oreSiUser.getPassword(), ""),
                Objects.toString(oreSiUser.getCreationDate(), ""));
        try {
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            final byte[] digest = sha.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12).toUpperCase(Locale.ROOT);
        } catch (final NoSuchAlgorithmException e) {
            throw new OreSiTechnicalException("SHA-256 digest unavailable in JVM", e);
        }
    }

    private static String getCollectAuthorizationForUser(final OreSiUser oreSiUser) {
        return oreSiUser.getAuthorizations().stream()
                .map(s -> String.format("%s", s))
                .collect(Collectors.joining("|", "name ~ '(", ")'"));
    }

    public static OreSiUserRole getUserRole(final OreSiUser user) {
        return OreSiUserRole.forUser(user);
    }

    /**
     * Reprend le role de l'utilisateur utilisé pour la connexion à la base de données
     */
    public void resetRole() {
        db.resetRole();
    }

    /**
     * Utilise le rôle de l'utilisateur courant pour l'accès à la base de données.
     */
    public void setRoleForClient() {
        final OreSiRoleToAccessDatabase roleToAccessDatabase = OreSiApiRequestContext.getRequestClientRole();
        setRole(roleToAccessDatabase);
    }

    public OreSiUser getCurrentUser() {
        return userRepository.findById(OreSiApiRequestContext.getRequestUserId());
    }

    /**
     * Prend le role du openAdomAdmin qui a le droit de tout faire
     */
    public OreSiopenAdomAdminRole setRoleAdmin() {
        setRole(OreSiRole.openAdomAdmin());
        return OreSiRole.openAdomAdmin();
    }

    /** Implémentation du port domaine {@link fr.inra.oresing.domain.port.AuthenticationPort#activateAdminRole()}. */
    @Override
    public void activateAdminRole() {
        setRoleAdmin();
    }

    /**
     * Prend le role du user passe en parametre, les requetes suivant ne pourra
     * pas faire des choses que l'utilisateur n'a pas le droit de faire
     */
    OreSiRoleToAccessDatabase setRole(final OreSiRoleToAccessDatabase roleToAccessDatabase) {
        db.setRole(roleToAccessDatabase);
        return roleToAccessDatabase;
    }

    /**
     * verifie que l'utilisateur existe et que son mot de passe est le bon
     *
     * @return l'objet OreSiUser contenant les informations sur l'utilisateur identifié
     */
    @Transactional
    public LoginAdminResult login(final String login, final String password) throws AuthenticationFailure {
        LoginAdminResult loginAdminResult = checkLoginPassword(login, password);
        return switch (loginAdminResult.state()) {
            case "active" -> loginAdminResult;
            case "idle" -> {
                sendValidationKey(userRepository.findByLogin(login).orElse(null));
                throw new AuthenticationFailure(AuthenticationFailure.INACTIVE_ACCOUNT, loginAdminResult);
            }
            case "pending" -> {
                sendValidationKey(userRepository.findByLogin(login).orElse(null));
                throw new AuthenticationFailure(AuthenticationFailure.PENDING_ACCOUNT, loginAdminResult);
            }
            default -> throw new AuthenticationFailure(AuthenticationFailure.CLOSED_ACCOUNT, loginAdminResult);

        };
    }

    /**
     * Variante explicitement contextuelle de {@link #checkLoginPassword(String, String)} .
     * Le code d'erreur emis depend de {@code context} - cf {@link fr.inra.oresing.domain.authorization.LoginPasswordCheckContext} .
     */
    @Override
    public LoginAdminResult checkLoginPassword(final String login, final String password, final fr.inra.oresing.domain.authorization.LoginPasswordCheckContext context) throws AuthenticationFailure {
        final Predicate<OreSiUser> checkPassword = user -> BCrypt.verifyer()
                .verify(password.toCharArray(), user.getPassword().toCharArray())
                .verified;
        CurrentUserRoles currentUserRoles = Optional.ofNullable(login)
                .map(this::getByIdOrLogin)
                .map(OreSiUser::getId)
                .map(UUID::toString)
                .map(this::getCurrentUserRoles)
                .orElse(null);
        return userRepository.findByLogin(login)
                .filter(checkPassword)
                .map(user -> toLoginResult(user, currentUserRoles))
                .orElseThrow(() -> new AuthenticationFailure(
                        context == fr.inra.oresing.domain.authorization.LoginPasswordCheckContext.UPDATE
                                ? AuthenticationFailure.BAD_CURRENT_PASSWORD
                                : AuthenticationFailure.BAD_LOGIN_PASSWORD,
                        (LoginAdminResult) null));
    }

    /**
     * Backward-compat : appel sans contexte explicite -> assume
     * {@link LoginPasswordCheckContext#LOGIN} ( comportement historique
     * du flow {@code POST /login} ) . Tous les nouveaux call sites
     * doivent passer le contexte explicitement via l'overload .
     */
    public LoginAdminResult checkLoginPassword(final String login, final String password) throws AuthenticationFailure {
        return checkLoginPassword(login, password, fr.inra.oresing.domain.authorization.LoginPasswordCheckContext.LOGIN);
    }

    public void sendEmailValidation(final String loginOrEmail) throws AuthenticationFailure {
        OreSiUser oreSiUser = userRepository.findByLoginOrEmail(loginOrEmail)
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_OR_EMAIL_PASSWORD, (LoginAdminResult) null));
        String verificationKey = generateVerificationKey(oreSiUser);
        serviceContainer.emailService().sendEmailValidation(oreSiUser.getLogin(), oreSiUser.getEmail(), verificationKey, EmailService.MESSAGES.NEW_EMAIL);
    }

    public OreSiUser sendEmailValidation(final OreSiUser loginResult, final EmailService.MESSAGES messages) throws AuthenticationFailure {
        setRoleAdmin();
        final Date updateDate = new Date();
        final OreSiUser oreSiUser = Optional.ofNullable(loginResult)
                .map(user -> userRepository.setState(user.getId(), user.getAccountstate()))
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.STATE_ERROR,
                        loginResult));
        final String verificationKey = generateVerificationKey(oreSiUser);
        userRepository.updateNewDate(oreSiUser, updateDate);
        setRoleForClient();
        serviceContainer.emailService().sendEmailValidation(oreSiUser.getLogin(), loginResult.getEmail(), verificationKey, messages);
        return oreSiUser;
    }

    /**
     * Valide la cle fournie par l'utilisateur ( forgot-password Phase 2 ou
     * activation de compte ) . Aucun effet de bord en cas d'echec : si la
     * cle est invalide ou expiree , on jette simplement BAD_VALIDATION_KEY
     * sans renvoyer un nouveau mail . L'utilisateur dispose d'un mecanisme
     * dedie de renvoi ( bouton "Renvoyer une nouvelle cle par email" en
     * UI ) , et un renvoi automatique a chaque tentative serait :
     * <ul>
     *   <li>contre-intuitif - le bouton "Valider" ne doit pas envoyer
     *       un mail ;</li>
     *   <li>une surface d'abus - un attaquant pourrait spammer l'inbox
     *       d'une victime en repetant des tentatives de cle ;</li>
     *   <li>generateur de bruit pour l'utilisateur honnete qui se trompe
     *       de cle .</li>
     * </ul>
     */
    public void validateValidationKey(OreSiUser oreSiUser, final String validationKey) throws AuthenticationFailure {
        OreSiUser oreSiUser1 = oreSiUser;
        String verificationKey = generateVerificationKey(oreSiUser1);
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime updateDate = oreSiUser1.getUpdateDate();
        final Duration duration = Duration.between(updateDate, now);
        if (!verificationKey.equals(validationKey)) {
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, oreSiUser1);
        }
        if (duration.compareTo(Duration.ofMinutes(validationKeyTtlMinutes)) >= 0) {
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, oreSiUser1);
        }
        setRoleAdmin();
        oreSiUser1.setAccountstate(OreSiUser.OreSiUserStates.active);
        oreSiUser1 = userRepository.setState(oreSiUser1.getId(), OreSiUser.OreSiUserStates.active);
        setRoleForClient();
        userRepository.findById(oreSiUser1.getId());
    }

    private LoginAdminResult toLoginResult(final OreSiUser oreSiUser, final CurrentUserRoles currentUserRoles) {
        final OreSiUserRole userRole = getUserRole(oreSiUser);
        db.setRole(userRole);
        db.hasRole(OreSiRole.openAdomAdmin());
        db.hasRole(OreSiRole.applicationCreator());
        return new LoginAdminResult(
                oreSiUser.getId(),
                oreSiUser.getLogin(),
                oreSiUser.getEmail(),
                oreSiUser.getAccountstate().name(),
                CurrentUserRolesResult.of(currentUserRoles),
                oreSiUser.getAuthorizations(),
                oreSiUser.getChartes()
        );
    }

    /**
     * Permet de créer un nouvel utilisateur
     *
     * @return l'objet OreSiUser qui vient d'être créé
     */
    @Transactional
    public CreateUserResult createUser(final String login, final String password, final String email) throws AuthenticationFailure {
        final Optional<OreSiUser> userByLogin = userRepository.findByLogin(login);
        if (userByLogin.isPresent()) {
            throw new AuthenticationFailure(AuthenticationFailure.EXISTING_LOGIN,
                    userByLogin.orElse(null));
        } else {
            final Optional<OreSiUser> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                throw new AuthenticationFailure(AuthenticationFailure.EXISTING_EMAIL,
                        userByEmail.orElse(null));
            }
        }
        final String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray());
        final OreSiUser result = new OreSiUser();
        result.setLogin(login);
        result.setPassword(bcrypted);
        result.setEmail(email);
        result.setChartes(new HashMap<>());
        result.setAccountstate(OreSiUser.OreSiUserStates.idle);
        userRepository.store(result);
        final OreSiUserRole userRole = getUserRole(result);
        db.createRoleWithPublic(userRole, "role de l'utilisateur %1$s".formatted(
                login
        ));
        return CreateUserResult.of(result);
    }

    @Transactional
    public OreSiUser deleteUserRightopenAdomAdmin(final UUID userId) {
        resetRole();
        final OreSiUserRole roleToModify = getUserRole(userId);
        db.removeUserInRole(roleToModify, OreSiopenAdomAdminRole.openAdomAdmin::getAsSqlRole);
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser addUserRightopenAdomAdmin(final UUID userId) {
        resetRole();
        final OreSiUserRole roleToModify = getUserRole(userId);
        db.addUserInRole(roleToModify, OreSiopenAdomAdminRole.openAdomAdmin::getAsSqlRole);
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser deleteUserRightCreateApplication(final UUID userId, final String applicationPattern) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        oreSiUser.getAuthorizations().remove(applicationPattern);
        final OreSiApplicationCreatorRole roleToAdd = OreSiRole.applicationCreator();
        db.removeUserInRole(roleToModify, roleToAdd);
        String expression = getCollectAuthorizationForUser(oreSiUser);
        SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.applicationCreator().getAsSqlRole(), userId.toString()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                userId::toString,
                expression,
                null
        );
        if (oreSiUser.getAuthorizations().isEmpty()) {
            db.dropPolicy(sqlPolicy);
        } else {
            db.createPolicy(sqlPolicy);
        }

        setRoleForClient();
        if (!Strings.isNullOrEmpty(applicationPattern)) {
            userRepository.updateAuthorizations(userId, oreSiUser.getAuthorizations());
            userRepository.flush();
        }
        resetRole();
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser deleteUserRightApplicationManager(final UUID userId, Application application) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        final OreSiRoleToBeGranted roleToAdd = (OreSiRoleToBeGranted) OreSiRole.applicationManagerOf(application);
        db.removeUserInRole(roleToModify, roleToAdd);
        String expression = getCollectAuthorizationForUser(oreSiUser);
        SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.applicationManagerOf(application).getAsSqlRole(), userId.toString()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                userId::toString,
                expression,
                null
        );
        db.dropPolicy(sqlPolicy);
        setRoleForClient();
        resetRole();
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser deleteUserRightUserManager(final UUID userId, Application application) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        final OreSiRoleToBeGranted roleToAdd = (OreSiRoleToBeGranted) OreSiRole.userManagerOf(application);
        db.removeUserInRole(roleToModify, roleToAdd);
        String expression = getCollectAuthorizationForUser(oreSiUser);
        SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.userManagerOf(application).getAsSqlRole(), userId.toString()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                userId::toString,
                expression,
                null
        );
        db.dropPolicy(sqlPolicy);
        setRoleForClient();
        resetRole();
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser addUserRightCreateApplication(final UUID userId, final String applicationPattern) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        oreSiUser.getAuthorizations().add(applicationPattern);
        final OreSiApplicationCreatorRole roleToAdd = OreSiRole.applicationCreator();
        db.addUserInRole(roleToModify, roleToAdd);
        String expression = getCollectAuthorizationForUser(oreSiUser);
        SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.applicationCreator().getAsSqlRole(), userId.toString()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                userId::toString,
                expression,
                null
        );
        db.createPolicy(sqlPolicy);
        setRoleForClient();
        if (!Strings.isNullOrEmpty(applicationPattern)) {
            userRepository.updateAuthorizations(userId, oreSiUser.getAuthorizations());
            userRepository.flush();
        }
        resetRole();
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser addUserRightApplicationManager(final UUID userId, Application application) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        final OreSiRoleToBeGranted roleToAdd = (OreSiRoleToBeGranted) OreSiRole.applicationManagerOf(application);
        db.addUserInRole(roleToModify, roleToAdd);
        String expression = getCollectAuthorizationForUser(oreSiUser);
        SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.applicationManagerOf(application).getAsSqlRole(), userId.toString()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                userId::toString,
                expression,
                null
        );
        db.createPolicy(sqlPolicy);
        setRoleForClient();
        resetRole();
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser addUserRightUserManager(final UUID userId, Application application) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        final OreSiRoleToBeGranted roleToAdd = (OreSiRoleToBeGranted) OreSiRole.userManagerOf(application);
        db.addUserInRole(roleToModify, roleToAdd);
        String expression = getCollectAuthorizationForUser(oreSiUser);
        SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.userManagerOf(application).getAsSqlRole(), userId.toString()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                userId::toString,
                expression,
                null
        );
        db.createPolicy(sqlPolicy);
        setRoleForClient();
        resetRole();
        return userRepository.findById(userId);
    }

    @Transactional
    public void removeUser(final UUID userId) {
        final OreSiUser oreSiUser = getOreSiUser(userId);
        final boolean deleted = userRepository.delete(userId);
        if (deleted) {
            final OreSiUserRole userRoleToDelete = getUserRole(oreSiUser);
            db.dropRole(userRoleToDelete);
        }
    }

    private OreSiUser getOreSiUser(final UUID userId) {
        return userRepository.tryFindById(userId)
                .orElseThrow(() -> new IllegalArgumentException("l'utilisateur " + userId + " n'existe pas en base"));
    }

    public OreSiUserRole getUserRole(final UUID userId) {
        final OreSiUser user = getOreSiUser(userId);
        return getUserRole(user);
    }

    public boolean hasRole(final UUID uuid, final OreSiRole role) {
        CurrentUserRoles rolesForRole = userRepository.getRolesForRole(uuid.toString());
        return rolesForRole.memberOf().contains(role.getAsSqlRole());
    }

    public CurrentUserRoles getCurrentUserRoles() {
        return userRepository.getRolesForCurrentUser();
    }

    public CurrentUserRoles getCurrentUserRoles(String userIdOrRoleName) {
        Optional<OreSiUser> oreSiUser = Optional.ofNullable(userIdOrRoleName)
                .flatMap(userRepository::findByLoginOrId);
        CurrentUserRoles rolesForCurrentUser = oreSiUser
                .map(OreSiEntity::getId)
                .map(UUID::toString)
                .map(id->userRepository.getRolesForCurrentUser(id))
                .orElse(userRepository.getRolesForCurrentUser(userIdOrRoleName));
        return rolesForCurrentUser;
    }

    public List<UserAuthorizationForApplication> getApplicationAuthorizations(Application application) {
        Function<Map<String, Timestamp>, Timestamp> getCharteTimestamp = chartes -> chartes.get(application.getId().toString());
        CurrentUserRoles currentUserRolesForCurrentUser = getCurrentUserRoles();
        if (currentUserRolesForCurrentUser.applicationManagerOf(application)) {
            return userRepository.findAll().stream()
                    .filter(oreSiUser -> OreSiUser.OreSiUserStates.active == oreSiUser.getAccountstate())
                    .map(oreSiUser -> {
                        Optional<Timestamp> timestampOpt = Optional.ofNullable(oreSiUser.getChartes())
                                .map(getCharteTimestamp);
                        CurrentUserRoles currentUserRoles = getCurrentUserRoles(oreSiUser.getId().toString());
                        return new UserAuthorizationForApplication(
                                application.getName(),
                                oreSiUser.getId(),
                                oreSiUser.getLogin(),
                                oreSiUser.getEmail(),
                                oreSiUser.getAccountstate().name(),
                                currentUserRoles.applicationManagerOf(application),
                                currentUserRoles.userManagerOf(application),
                                oreSiUser.getAuthorizations(),
                                timestampOpt.map(timestamp -> timestamp.after(Timestamp.from(Instant.now()))).orElse(false),
                                timestampOpt.isPresent()
                        );
                    })
                    .toList();
        } else if (currentUserRolesForCurrentUser.userManagerOf(application)) {
            return userRepository.findAll().stream()
                    .filter(oreSiUser -> OreSiUser.OreSiUserStates.active == oreSiUser.getAccountstate())
                    .map(oreSiUser -> {
                        Optional<Timestamp> timestampOpt = Optional.ofNullable(oreSiUser.getChartes())
                                .map(getCharteTimestamp);
                        return new UserAuthorizationForApplication(
                                application.getName(),
                                oreSiUser.getId(),
                                oreSiUser.getLogin(),
                                oreSiUser.getEmail(),
                                oreSiUser.getAccountstate().name(),
                                null,
                                hasRole(oreSiUser.getId(), OreSiRole.userManagerOf(application)),
                                oreSiUser.getAuthorizations(),
                                timestampOpt.map(timestamp -> timestamp.after(Timestamp.from(Instant.now()))).orElse(false),
                                timestampOpt.isPresent()
                        );
                    })
                    .toList();
        } else {
            throw new NotOpenAdomAdminException();
        }
    }

    public List<LoginAdminResult> getAdminAuthorizations() {
        CurrentUserRoles currentUserRoles = getCurrentUserRoles();
        if (currentUserRoles.isOpenAdomAdmin()) {
            return userRepository.findAll().stream()
                    .filter(oreSiUser -> OreSiUser.OreSiUserStates.active == oreSiUser.getAccountstate())
                    .map(user -> {
                        OreSiUserRole userRole = getUserRole(user.getId());
                        return toLoginResult(user, getCurrentUserRoles(userRole.getAsSqlRole()));
                    })
                    .toList();
        } else if (currentUserRoles.isApplicationCreator()) {
            return userRepository.findAll().stream()
                    .map(user -> {
                        OreSiUserRole userRole = getUserRole(user.getId());
                        return toLoginResult(user, getCurrentUserRoles(userRole.getAsSqlRole()));
                    })
                    .toList();
        } else {
            throw new NotOpenAdomAdminException();//TODO
        }
    }

    public OreSiUser getByIdOrLogin(final String userIdOrLogin) {
        return userRepository.findByLogin(userIdOrLogin)
                .orElseGet(() -> {
                    UUID id;
                    try {
                        id = UUID.fromString(userIdOrLogin);
                    } catch (Exception e) {
                        return null;
                    }
                    return userRepository.findById(id);
                });
    }

    @Transactional
    public OreSiUser updateUser(NotConnectedUser notConnectedUser) throws AuthenticationFailure, JsonProcessingException {
        return switch (notConnectedUser) {
            case NotConnectedAuthentifiedActiveUser(OreSiUser user, CreateUserRequest createUserRequest )  -> updateAccount(
                    user, createUserRequest);
            case NotConnectedAuthentifiedActiveUserNotSignedCharte(
                    OreSiUser user,
                    CreateUserRequest _,
                    String charte) ->
                    setCharteAsValidated(user, charte);
            case NotConnectedAuthentifiedClosedUser(LoginAdminResult loginAdminResult) ->
                    throw new AuthenticationFailure(AuthenticationFailure.CLOSED_ACCOUNT, loginAdminResult);
            case NotConnectedAuthentifiedIdleUser(OreSiUser user,
                                                  CreateUserRequest createUserRequest) ->
                    activeAccount(user, createUserRequest.getVerificationKey());
            case NotConnectedAuthentifiedMissingPasswordUser(OreSiUser oreSiUser,
                                                             CreateUserRequest createUserRequest) ->
                    updatePasswordLost(oreSiUser, createUserRequest);
            case NotConnectedAuthentifiedPendingUser(OreSiUser user) ->
                    sendValidationKey(user);
            case NotConnectedUnauthentifiedUser(CreateUserRequest createUserRequest) ->
                    throw new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_OR_EMAIL_PASSWORD, createUserRequest);
            case NotConnectedUnauthentifiedUserForCreate _ -> null;
        };
    }

    private OreSiUser setCharteAsValidated(final OreSiUser loginResult, final String charte) {
        return Optional.ofNullable(loginResult)
                .map(oreSiUser -> {
                    final Map<String, Timestamp> chartes = oreSiUser.getChartes() == null ? new HashMap<>() : oreSiUser.getChartes();
                    chartes.put(charte, Timestamp.from(Instant.now()));
                    oreSiUser.setChartes(chartes);
                    try {
                        return userRepository.update(oreSiUser);
                    } catch (final JsonProcessingException e) {
                        throw new OreSiTechnicalException(ExceptionMessage.JSON_PROCESSING.toMessage(), e);
                    }
                })
                .orElse(new OreSiUser());
    }

    private OreSiUser sendValidationKey(final OreSiUser loginResult) throws AuthenticationFailure {
        return sendEmailValidation(loginResult, EmailService.MESSAGES.VALIDATION_KEY);
    }

    private OreSiUser updatePasswordLost(final OreSiUser loginResult, final CreateUserRequest createUserRequest) throws AuthenticationFailure, JsonProcessingException {
        final OreSiUser oreSiUser = Optional.ofNullable(loginResult)
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_PASSWORD, (LoginAdminResult) null));
        validateValidationKey(oreSiUser, createUserRequest.getVerificationKey());
        final String verifiedPassword = Optional.ofNullable(createUserRequest.getNewPassword())
                .filter(password -> !Strings.isNullOrEmpty(password))
                .filter(password -> password.equals(createUserRequest.getNewPasswordConfirm()))
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_PASSWORDS, (LoginAdminResult) null));
        final String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, verifiedPassword.toCharArray());
        oreSiUser.setPassword(bcrypted);
        setRoleAdmin();
        final OreSiUser update = userRepository.update(oreSiUser);
        setRoleForClient();
        return update;
    }

    /**
     * Route le PUT /users d'un utilisateur deja authentifie ( ActiveUser ) vers
     * la sous-action adequate selon le payload :
     * <ul>
     *     <li>{@code verificationKey} present -> phase 2 d'un changement d'email
     *         en cours : {@link #commitEmailChange} valide la cle et swap
     *         atomique pendingEmail -> email ;
     *     <li>{@code newPassword} present : applique le changement de mdp ;
     *     <li>{@code email} different de user.email : phase 1 d'un changement
     *         d'email - enregistre uniquement {@code pendingEmail} et envoie
     *         la cle ; {@code email} et {@code accountstate} INCHANGES pour
     *         que l'utilisateur reste pleinement actif pendant la validation
     *         ( cf. bug "wrong validation key locks out user" ) .
     * </ul>
     */
    private OreSiUser updateAccount(final OreSiUser user, final CreateUserRequest createUserRequest) throws AuthenticationFailure, JsonProcessingException {
        // Phase 2 declenchee uniquement si verificationKey ET un changement
        // est effectivement en attente . Si l'appelant passe une cle alors
        // que pending_email est null ( cas typique : PUT combine email +
        // newPassword + verificationKey decorative post-validation ) , on
        // ignore la cle et on traite les autres champs - ne pas le faire
        // brise les flow legacy ou la cle est toleree sans pending_email .
        final boolean hasVerificationKey = !Strings.isNullOrEmpty(createUserRequest.getVerificationKey());
        final boolean hasPendingEmail = !Strings.isNullOrEmpty(user.getPendingEmail());
        if (hasVerificationKey && hasPendingEmail) {
            return commitEmailChange(user, createUserRequest.getVerificationKey());
        }
        final String requestedEmail = Optional.ofNullable(createUserRequest.getEmail())
                .filter(mail -> !Strings.isNullOrEmpty(mail))
                .orElse(user.getEmail())
                .toLowerCase();
        final boolean emailChanged = !user.getEmail().toLowerCase().equals(requestedEmail);
        if (createUserRequest.getNewPassword() != null) {
            final String verifiedPassword = Optional.of(createUserRequest.getNewPassword())
                    .filter(password -> !Strings.isNullOrEmpty(password))
                    .filter(password -> password.equals(createUserRequest.getNewPasswordConfirm()))
                    .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_PASSWORDS, (LoginAdminResult) null));
            final String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, verifiedPassword.toCharArray());
            user.setPassword(bcrypted);
        }
        if (emailChanged) {
            // Pre-check unicite : si un AUTRE utilisateur a deja cet email ,
            // refus immediat sans envoi de mail ni mutation DB . Sans ce
            // garde-fou , le commit ( phase 2 ) declencherait une violation
            // de contrainte unique {@code oresiuser_email_key} -> 500
            // cryptique au lieu d'un message d'erreur metier clair .
            final Optional<OreSiUser> collidingUser = userRepository.findByEmail(requestedEmail);
            if (collidingUser.isPresent() && !collidingUser.get().getId().equals(user.getId())) {
                throw new AuthenticationFailure(AuthenticationFailure.EXISTING_EMAIL, collidingUser.get());
            }
            // Phase 1 atomique "send first , persist after" :
            //   1. compute key bound to pendingEmail ( in-memory )
            //   2. send mail SMTP -> throws MailServiceUnavailableException si KO
            //   3. mail OK -> persist pending_email + updateDate ( ancrage TTL )
            // En cas d'erreur SMTP a l'etape 2 , aucune mutation DB n'a ete
            // appliquee -> retry direct utilisateur sans etat corrompu .
            user.setPendingEmail(requestedEmail);
            final String verificationKey = generateVerificationKey(user, requestedEmail);
            serviceContainer.emailService().sendEmailValidation(
                    user.getLogin(),
                    requestedEmail,
                    verificationKey,
                    EmailService.MESSAGES.NEW_EMAIL);
            // Mail OK , on peut persister . updateNewDate refresh updateDate
            // ( TTL anchor ) + persiste pending_email dans la meme ecriture .
            setRoleAdmin();
            userRepository.updateNewDate(user, new java.sql.Timestamp(System.currentTimeMillis()));
            final OreSiUser updateUser = userRepository.update(user);
            setRoleForClient();
            return updateUser;
        }
        // Garde-fou : on arrive ici sans changement d'email ET sans changement
        // de mot de passe ( newPassword null traite plus haut sinon ) ET sans
        // cle de validation ( commitEmailChange traite au tout debut ) . L'
        // appelant a effectue une requete vide - typiquement frontend phase 1
        // avec newEmail == email courant . On NE DOIT PAS retourner 200 sinon
        // le frontend afficherait un toast vert "mail envoye" alors qu'aucun
        // mail n'a ete envoye . Invariant : 200 OK <=> mail envoye .
        if (createUserRequest.getNewPassword() == null) {
            throw new AuthenticationFailure(AuthenticationFailure.EMAIL_UNCHANGED, user);
        }
        // Changement de mot de passe pur ( email identique , verifie plus haut
        // que newPassword n'est pas null ) : on persiste juste le hash deja
        // calcule dans user .
        setRoleAdmin();
        final OreSiUser updateUser = userRepository.update(user);
        setRoleForClient();
        return updateUser;
    }

    /**
     * Phase 2 d'un changement d'email : valide la cle puis swap atomique
     * {@code pendingEmail} -> {@code email} . En cas de cle invalide ,
     * {@link #validateValidationKey} jette BAD_VALIDATION_KEY et aucune
     * modification n'est appliquee ( pending_email + email inchanges ) .
     *
     * @throws AuthenticationFailure BAD_VALIDATION_KEY si la cle est
     *         invalide ou expiree ; NO_PENDING_EMAIL_CHANGE si l'appelant
     *         soumet une cle alors qu'aucun changement n'est en attente
     *         ( cas degenere : double-soumission apres succes , ou
     *         appel direct hors flow ) .
     */
    private OreSiUser commitEmailChange(final OreSiUser user, final String verificationKey) throws AuthenticationFailure, JsonProcessingException {
        if (Strings.isNullOrEmpty(user.getPendingEmail())) {
            throw new AuthenticationFailure(AuthenticationFailure.NO_PENDING_EMAIL_CHANGE, user);
        }
        // Verification bindee au pendingEmail ( pas user.email ) : meme cle
        // utilisee a la phase 1 ( cf sendEmailChangeValidation ) , garantit
        // la coherence avant et apres le swap email <- pendingEmail .
        final String expected = generateVerificationKey(user, user.getPendingEmail());
        if (!expected.equals(verificationKey)) {
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, user);
        }
        // TTL identique aux autres flows ( 10 min depuis updateDate ) .
        final Duration duration = Duration.between(user.getUpdateDate(), LocalDateTime.now());
        if (duration.compareTo(Duration.ofMinutes(validationKeyTtlMinutes)) >= 0) {
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, user);
        }
        // Re-check unicite juste avant le swap : entre phase 1 et phase 2
        // ( fenetre TTL pouvant atteindre {@code validationKeyTtlMinutes} ) ,
        // un autre utilisateur peut avoir pris l'email cible . Sans ce
        // recheck , la contrainte unique {@code oresiuser_email_key}
        // declencherait une violation SQL -> 500 cryptique . On detecte
        // la collision tot pour rendre une erreur metier explicite et
        // laisser pending_email intact pour analyse cote utilisateur .
        final Optional<OreSiUser> collidingUser = userRepository.findByEmail(user.getPendingEmail());
        if (collidingUser.isPresent() && !collidingUser.get().getId().equals(user.getId())) {
            throw new AuthenticationFailure(AuthenticationFailure.EXISTING_EMAIL, collidingUser.get());
        }
        setRoleAdmin();
        user.setEmail(user.getPendingEmail());
        user.setPendingEmail(null);
        final OreSiUser updateUser = userRepository.update(user);
        setRoleForClient();
        return updateUser;
    }


    private OreSiUser activeAccount(final OreSiUser oreSiUser, final String verificationKey) throws AuthenticationFailure {
        validateValidationKey(oreSiUser, verificationKey);
        return oreSiUser;
    }

}