package fr.inra.oresing.persistence;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.*;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.model.authorization.CurrentUserRolesResult;
import fr.inra.oresing.rest.CreateUserResult;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.domain.exceptions.authentication.authentication.NotopenAdomAdminException;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.model.authorization.LoginApplicationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
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
public class AuthenticationService {
    @Autowired
    EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SqlService db;

    @Autowired
    private OreSiApiRequestContext request;

    @Value("${bcryptCost:12}")
    private int bcryptCost;

    private static String generateVerificationKey(final OreSiUser oreSiUser) {
        final String s = oreSiUser.getEmail() + oreSiUser.getPassword() + oreSiUser.getCreationDate().toString();
        return (Math.abs(s.hashCode() * 15621646) + "454996856456").substring(0, 10);
    }

    private static String getCollectAuthorizationForUser(final OreSiUser oreSiUser) {
        return oreSiUser.getAuthorizations().stream()
                .map(s -> String.format("%s", s))
                .collect(Collectors.joining("|", "name ~ '(", ")'"));
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
    public OreSiRoleToAccessDatabase setRoleForClient() {
        final OreSiRoleToAccessDatabase roleToAccessDatabase = request.getRequestClient().role();
        setRole(roleToAccessDatabase);
        return roleToAccessDatabase;
    }

    /**
     * Prend le role du openAdomAdmin qui a le droit de tout faire
     */
    public OreSiopenAdomAdminRole setRoleAdmin() {
        setRole(OreSiRole.openAdomAdmin());
        return OreSiRole.openAdomAdmin();
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
    public LoginAdminResult login(final String login, final String password) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        LoginAdminResult loginAdminResult = checkLoginPassword(login, password);
        return switch (loginAdminResult.state()) {
            case "active" -> loginAdminResult;
            case "idle" -> {
                sendValidationKey(userRepository.findByLogin(login));
                throw new AuthenticationFailure(AuthenticationFailure.INACTIVE_ACCOUNT, loginAdminResult);
            }
            case "pending" -> {
                sendValidationKey(userRepository.findByLogin(login));
                throw new AuthenticationFailure(AuthenticationFailure.PENDING_ACCOUNT, loginAdminResult);
            }
            default -> throw new AuthenticationFailure(AuthenticationFailure.CLOSED_ACCOUNT, loginAdminResult);

        };
    }

    private LoginAdminResult checkLoginPassword(final String login, final String password) throws AuthenticationFailure {
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
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_PASSWORD, (LoginAdminResult) null));
    }

    public OreSiUser sendEmailValidation(final String loginOrEmail, final String password) throws AuthenticationFailure {
        OreSiUser oreSiUser = userRepository.findByLoginOrEmail(loginOrEmail)
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_OR_EMAIL_PASSWORD, (LoginAdminResult) null));
        String verificationKey = generateVerificationKey(oreSiUser);
        emailService.sendEmailValidation(oreSiUser.getLogin(), oreSiUser.getEmail(), verificationKey, EmailService.MESSAGES.NEW_EMAIL);
        return oreSiUser;
    }

    public OreSiUser sendEmailValidation(final Optional<OreSiUser> loginResult, final EmailService.MESSAGES messages) throws AuthenticationFailure, JsonProcessingException {
        setRoleAdmin();
        final Date updateDate = new Date();
        final OreSiUser oreSiUser = loginResult
                .map(user -> userRepository.setState(user.getId(), user.getAccountstate()))
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.STATE_ERROR,
                        loginResult.orElse(null)));
        final String verificationKey = generateVerificationKey(oreSiUser);
        userRepository.updateNewDate(oreSiUser, updateDate);
        setRoleForClient();
        emailService.sendEmailValidation(loginResult.get().getLogin(), loginResult.get().getEmail(), verificationKey, messages);
        return oreSiUser;
    }

    public OreSiUser validateValidationKey(OreSiUser oreSiUser, final String validationKey) throws NoSuchAlgorithmException, InvalidKeySpecException, AuthenticationFailure, JsonProcessingException {
        OreSiUser oreSiUser1 = oreSiUser;
        String verificationKey = generateVerificationKey(oreSiUser1);
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime updateDate = oreSiUser1.getUpdateDate();
        final Duration duration = Duration.between(updateDate, now);
        if (!verificationKey.equals(validationKey)) {
            sendValidationKey(Optional.of(oreSiUser1));
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, oreSiUser1);
        }
        if (duration.compareTo(Duration.ofMinutes(10)) < 0) {
            setRoleAdmin();
            oreSiUser1.setAccountstate(OreSiUser.OreSiUserStates.active);
            oreSiUser1 = userRepository.setState(oreSiUser1.getId(), OreSiUser.OreSiUserStates.active);
            setRoleForClient();
        } else {
            sendValidationKey(Optional.of(oreSiUser1));
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, oreSiUser1);
        }
        return userRepository.findById(oreSiUser1.getId());
    }

    private LoginAdminResult toLoginResult(final OreSiUser oreSiUser, final CurrentUserRoles currentUserRoles) {
        final OreSiUserRole userRole = getUserRole(oreSiUser);
        db.setRole(userRole);
        final boolean isopenAdomAdmin = db.hasRole(OreSiRole.openAdomAdmin());
        final boolean authorizedForApplicationCreation = db.hasRole(OreSiRole.applicationCreator());
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
    public CreateUserResult createRole(final UUID id) {
        //Preconditions.checkArgument(userRepository.findByLogin(id.toString()).isEmpty(), "Il existe déjà un rôle dont l’identifiant est " + id.toString());
        final OreSiUser result = new OreSiUser();
        result.setLogin(id.toString());
        result.setChartes(new HashMap<>());
        final OreSiUserRole userRole = getUserRole(result);
        db.createRole(userRole, "role de l'utilisateur %1$s".formatted(
                result.getLogin()
        ));
        return CreateUserResult.of(result);
    }

    @Transactional
    public OreSiUser deleteUserRightopenAdomAdmin(final UUID userId) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        final OreSiopenAdomAdminRole roleToRevoke = OreSiRole.openAdomAdmin();
        db.removeUserInRole(roleToModify, () -> OreSiopenAdomAdminRole.openAdomAdmin.getAsSqlRole());
        return userRepository.findById(userId);
    }

    @Transactional
    public OreSiUser addUserRightopenAdomAdmin(final UUID userId) {
        resetRole();
        OreSiUser oreSiUser = getOreSiUser(userId);
        final OreSiUserRole roleToModify = getUserRole(userId);
        final OreSiopenAdomAdminRole roleToAdd = OreSiRole.openAdomAdmin();
        db.addUserInRole(roleToModify, () -> OreSiopenAdomAdminRole.openAdomAdmin.getAsSqlRole());
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
                () -> userId.toString(),
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
                () -> userId.toString(),
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
                () -> userId.toString(),
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
                () -> userId.toString(),
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
                () -> userId.toString(),
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
                () -> userId.toString(),
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
        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser(userIdOrRoleName);
        if (oreSiUser.isPresent()) {
            rolesForCurrentUser = rolesForCurrentUser.withUSer(oreSiUser.get());
        }
        return rolesForCurrentUser;
    }

    public static OreSiUserRole getUserRole(final OreSiUser user) {
        return OreSiUserRole.forUser(user);
    }

    public List<LoginApplicationResult> getApplicationAuthorizations(Application application) {
        Function<Map<String, Timestamp>, Timestamp> getCharteTimestamp = chartes -> chartes.get(application.getId().toString());
        CurrentUserRoles currentUserRolesForCurrentUser = getCurrentUserRoles();
        if (currentUserRolesForCurrentUser.applicationManagerOf(application)) {
            return userRepository.findAll().stream()
                    .filter(oreSiUser -> OreSiUser.OreSiUserStates.active == oreSiUser.getAccountstate())
                    .map(oreSiUser -> {
                        Optional<Timestamp> timestampOpt = Optional.ofNullable(oreSiUser.getChartes())
                                .map(getCharteTimestamp);
                        CurrentUserRoles currentUserRoles = getCurrentUserRoles(oreSiUser.getId().toString());
                        return new LoginApplicationResult(
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
                    .collect(Collectors.toList());
        } else if (currentUserRolesForCurrentUser.userManagerOf(application)) {
            return userRepository.findAll().stream()
                    .filter(oreSiUser -> OreSiUser.OreSiUserStates.active == oreSiUser.getAccountstate())
                    .map(oreSiUser -> {
                        Optional<Timestamp> timestampOpt = Optional.ofNullable(oreSiUser.getChartes())
                                .map(getCharteTimestamp);
                        return new LoginApplicationResult(
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
                    .collect(Collectors.toList());
        } else {
            throw new NotopenAdomAdminException();
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
                    .collect(Collectors.toList());
        } else if (currentUserRoles.isApplicationCreator()) {
            return userRepository.findAll().stream()
                    .map(user -> {
                        OreSiUserRole userRole = getUserRole(user.getId());
                        return toLoginResult(user, getCurrentUserRoles(userRole.getAsSqlRole()));
                    })
                    .collect(Collectors.toList());
        } else {
            throw new NotopenAdomAdminException();
        }
    }

    public OreSiUser getByIdOrLogin(final String userIdOrLogin) {
        return userRepository.findByLogin(userIdOrLogin)
                .orElseGet(() -> {
                    UUID id = null;
                    try {
                        id = UUID.fromString(userIdOrLogin);
                    } catch (Exception e) {
                        return null;
                    }
                    return userRepository.findById(id);
                });
    }

    @Transactional
    public OreSiUser updateUser(final CreateUserRequest createUserRequest) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        final String login = createUserRequest.getLogin();
        final String password = createUserRequest.getPassword();
        final String email = createUserRequest.getEmail();
        final String verificationKey = createUserRequest.getVerificationKey();
        final String charte = createUserRequest.getCharte();
        if (!Strings.isNullOrEmpty(login) && !Strings.isNullOrEmpty(password)) {
            final LoginAdminResult loginAdminResult = checkLoginPassword(login, password);
            final OreSiUser user = userRepository.findById(loginAdminResult.id());
            if (Strings.isNullOrEmpty(verificationKey)) {
                return updateAccount(user, createUserRequest);
            } else if (OreSiUser.OreSiUserStates.active == OreSiUser.OreSiUserStates.valueOf(loginAdminResult.state())) {
                return updateAccount(user, createUserRequest);
            } else {
                return activeAccount(loginAdminResult, verificationKey);
            }
        } else if (!Strings.isNullOrEmpty(login) && !Strings.isNullOrEmpty(email)) {
            final Optional<OreSiUser> loginResult = userRepository.findByLoginAndEmail(login, email);
            loginResult.orElseThrow(() -> new AuthenticationFailure(
                    AuthenticationFailure.INVALID_ACCOUNT,
                    new LoginAdminResult(null,
                            login,
                            email,
                            "",
                            null,
                            Set.of(),
                            Map.of()
                    )));
            if (!Strings.isNullOrEmpty(charte)) {
                return setCharteAsValidated(loginResult, createUserRequest, charte);
            } else if (!Strings.isNullOrEmpty(verificationKey)) {
                return updatePasswordLost(loginResult, createUserRequest);
            } else {
                return sendValidationKey(loginResult);
            }
        }
        return null;
    }

    private OreSiUser setCharteAsValidated(final Optional<OreSiUser> loginResult, final CreateUserRequest createUserRequest, final String charte) {
        return loginResult
                .map(oreSiUser -> {
                    final Map<String, Timestamp> chartes = oreSiUser.getChartes() == null ? new HashMap<>() : oreSiUser.getChartes();
                    chartes.put(charte, Timestamp.from(Instant.now()));
                    oreSiUser.setChartes(chartes);
                    try {
                        return userRepository.update(oreSiUser);
                    } catch (final JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(new OreSiUser());
    }

    private OreSiUser sendValidationKey(final Optional<OreSiUser> loginResult) throws NoSuchAlgorithmException, InvalidKeySpecException, AuthenticationFailure, JsonProcessingException {
        return sendEmailValidation(loginResult, EmailService.MESSAGES.VALIDATION_KEY);
    }

    private OreSiUser updatePasswordLost(final Optional<OreSiUser> loginResult, final CreateUserRequest createUserRequest) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        final OreSiUser oreSiUser = loginResult
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_PASSWORD, (LoginAdminResult) null));
        validateValidationKey(oreSiUser, createUserRequest.getVerificationKey());
        Optional.ofNullable(createUserRequest.getNewPassword())
                .filter(password -> !Strings.isNullOrEmpty(password))
                .filter(password -> password.equals(createUserRequest.getNewPasswordConfirm()))
                .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_PASSWORDS, (LoginAdminResult) null));
        final String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, createUserRequest.getNewPassword().toCharArray());
        oreSiUser.setPassword(bcrypted);
        setRoleAdmin();
        final OreSiUser update = userRepository.update(oreSiUser);
        setRoleForClient();
        return update;
    }

    private OreSiUser updateAccount(final OreSiUser user, final CreateUserRequest createUserRequest) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        final String email = Optional.ofNullable(createUserRequest.getEmail())
                .filter(mail -> !Strings.isNullOrEmpty(mail))
                .orElse(user.getEmail())
                .toLowerCase();
        final boolean emailChanged = !user.getEmail().toLowerCase().equals(email);
        if (createUserRequest.getNewPassword() != null) {
            Optional.of(createUserRequest.getNewPassword())
                    .filter(password -> !Strings.isNullOrEmpty(password))
                    .filter(password -> password.equals(createUserRequest.getNewPasswordConfirm()))
                    .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_PASSWORDS, (LoginAdminResult) null));
            final String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, createUserRequest.getNewPassword().toCharArray());
            user.setPassword(bcrypted);
        }
        if (emailChanged) {
            user.setAccountstate(OreSiUser.OreSiUserStates.pending);
            user.setEmail(createUserRequest.getEmail().toLowerCase());
        }
        setRoleAdmin();
        final OreSiUser updateUser = userRepository.update(user);
        if (emailChanged) {
            sendEmailValidation(Optional.of(updateUser), EmailService.MESSAGES.NEW_EMAIL);
        }
        setRoleForClient();
        return updateUser;
    }

    private OreSiUser activeAccount(final LoginAdminResult loginAdminResult, final String verificationKey) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        OreSiUser user = userRepository.findById(loginAdminResult.id());
        user = validateValidationKey(user, verificationKey);
        return user;
    }

}