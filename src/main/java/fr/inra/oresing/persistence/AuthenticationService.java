package fr.inra.oresing.persistence;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.AuthenticationServiceImpl;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdminException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.repository.authorization.role.*;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.CreateUserResult;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.model.authorization.CurrentUserRolesResult;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.model.authorization.UserAuthorizationForApplication;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
public class AuthenticationService implements ServiceContainerBean, AuthenticationServiceImpl {
    @Setter
    private ServiceContainer serviceContainer;

    private final UserRepository userRepository;

    private final SqlService db;

    private final OreSiApiRequestContext request;

    @Value("${bcryptCost:12}")
    private int bcryptCost;

    public AuthenticationService(UserRepository userRepository, SqlService db, OreSiApiRequestContext request) {
        this.userRepository = userRepository;
        this.db = db;
        this.request = request;
    }

    private static String generateVerificationKey(final OreSiUser oreSiUser) {
        final String s = oreSiUser.getEmail() + oreSiUser.getPassword() + oreSiUser.getCreationDate().toString();
        return (Math.abs(s.hashCode() * 15621646) + "454996856456").substring(0, 10);
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
        final OreSiRoleToAccessDatabase roleToAccessDatabase = request.getRequestClientRole();
        setRole(roleToAccessDatabase);
    }

    public OreSiUser getCurrentUser() {
        return userRepository.findById(request.getRequestUserId());
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

    public LoginAdminResult checkLoginPassword(final String login, final String password) throws AuthenticationFailure {
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

    public void validateValidationKey(OreSiUser oreSiUser, final String validationKey) throws AuthenticationFailure {
        OreSiUser oreSiUser1 = oreSiUser;
        String verificationKey = generateVerificationKey(oreSiUser1);
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime updateDate = oreSiUser1.getUpdateDate();
        final Duration duration = Duration.between(updateDate, now);
        if (!verificationKey.equals(validationKey)) {
            sendValidationKey(oreSiUser1);
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, oreSiUser1);
        }
        if (duration.compareTo(Duration.ofMinutes(10)) < 0) {
            setRoleAdmin();
            oreSiUser1.setAccountstate(OreSiUser.OreSiUserStates.active);
            oreSiUser1 = userRepository.setState(oreSiUser1.getId(), OreSiUser.OreSiUserStates.active);
            setRoleForClient();
        } else {
            sendValidationKey(oreSiUser1);
            throw new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, oreSiUser1);
        }
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
        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser(userIdOrRoleName);
        if (oreSiUser.isPresent()) {
            rolesForCurrentUser = rolesForCurrentUser.withUSer(oreSiUser.get());
        }
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

    private OreSiUser updateAccount(final OreSiUser user, final CreateUserRequest createUserRequest) throws AuthenticationFailure, JsonProcessingException {
        final String email = Optional.ofNullable(createUserRequest.getEmail())
                .filter(mail -> !Strings.isNullOrEmpty(mail))
                .orElse(user.getEmail())
                .toLowerCase();
        final boolean emailChanged = !user.getEmail().toLowerCase().equals(email);
        if (createUserRequest.getNewPassword() != null) {
            final String verifiedPassword = Optional.of(createUserRequest.getNewPassword())
                    .filter(password -> !Strings.isNullOrEmpty(password))
                    .filter(password -> password.equals(createUserRequest.getNewPasswordConfirm()))
                    .orElseThrow(() -> new AuthenticationFailure(AuthenticationFailure.BAD_PASSWORDS, (LoginAdminResult) null));
            final String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, verifiedPassword.toCharArray());
            user.setPassword(bcrypted);
        }
        if (emailChanged) {
            user.setAccountstate(OreSiUser.OreSiUserStates.pending);
            user.setEmail(createUserRequest.getEmail().toLowerCase());
        }
        setRoleAdmin();
        final OreSiUser updateUser = userRepository.update(user);
        if (emailChanged) {
            sendEmailValidation(updateUser, EmailService.MESSAGES.NEW_EMAIL);
        }
        setRoleForClient();
        return updateUser;
    }

    private OreSiUser activeAccount(final OreSiUser oreSiUser, final String verificationKey) throws AuthenticationFailure {
        validateValidationKey(oreSiUser, verificationKey);
        return oreSiUser;
    }

}