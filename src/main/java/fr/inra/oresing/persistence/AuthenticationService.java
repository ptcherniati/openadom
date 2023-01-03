package fr.inra.oresing.persistence;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import fr.inra.oresing.model.CreateUserResult;
import fr.inra.oresing.model.LoginResult;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.*;
import fr.inra.oresing.rest.*;
import fr.inra.oresing.rest.exceptions.NotSuperAdminException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Transactional
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SqlService db;

    @Autowired
    private OreSiApiRequestContext request;

    @Value("${bcryptCost:12}")
    private int bcryptCost;

    /**
     * Reprend le role de l'utilisateur utilisé pour la connexion à la base de données
     */
    public void resetRole() {
        db.resetRole();
    }

    /**
     * Utilise le rôle de l'utilisateur courant pour l'accès à la base de données.
     *
     * @return
     */
    public OreSiRoleToAccessDatabase setRoleForClient() {
        OreSiRoleToAccessDatabase roleToAccessDatabase = request.getRequestClient().getRole();
        setRole(roleToAccessDatabase);
        return roleToAccessDatabase;
    }

    /**
     * Prend le role du superadmin qui a le droit de tout faire
     *
     * @return
     */
    public OreSiSuperAdminRole setRoleAdmin() {
        setRole(OreSiRole.superAdmin());
        return OreSiRole.superAdmin();
    }

    /**
     * Prend le role du user passe en parametre, les requetes suivant ne pourra
     * pas faire des choses que l'utilisateur n'a pas le droit de faire
     *
     * @return
     */
    OreSiRoleToAccessDatabase setRole(OreSiRoleToAccessDatabase roleToAccessDatabase) {
        db.setRole(roleToAccessDatabase);
        return roleToAccessDatabase;
    }

    /**
     * verifie que l'utilisateur existe et que son mot de passe est le bon
     *
     * @return l'objet OreSiUser contenant les informations sur l'utilisateur identifié
     */
    public LoginResult login(String login, String password) throws AuthenticationFailure {
        Predicate<OreSiUser> checkPassword = user -> BCrypt.verifyer().verify(password.toCharArray(), user.getPassword().toCharArray()).verified;
        return userRepository.findByLogin(login)
                .filter(checkPassword)
                .map(this::toLoginResult)
                .orElseThrow(() -> new AuthenticationFailure("identifiants fournis incorrects"));
    }

    private LoginResult toLoginResult(OreSiUser oreSiUser) {
        OreSiUserRole userRole = getUserRole(oreSiUser);
        db.setRole(userRole);
        boolean authorizedForApplicationCreation = db.hasRole(OreSiRole.applicationCreator());
        boolean isSuperAdmin = db.hasRole(OreSiRole.superAdmin());
        return new LoginResult(oreSiUser.getId(), oreSiUser.getLogin(), authorizedForApplicationCreation,isSuperAdmin, oreSiUser.getAuthorizations());
    }

    /**
     * Permet de créer un nouvel utilisateur
     *
     * @return l'objet OreSiUser qui vient d'être créé
     */
    public CreateUserResult createUser(String login, String password) {
        Preconditions.checkArgument(userRepository.findByLogin(login).isEmpty(), "Il existe déjà un utilisateur dont l’identifiant est " + login);
        String bcrypted = BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray());
        OreSiUser result = new OreSiUser();
        result.setLogin(login);
        result.setPassword(bcrypted);
        userRepository.store(result);
        OreSiUserRole userRole = getUserRole(result);
        db.createRoleWithPublic(userRole);
        return new CreateUserResult(result.getId());
    }

    public CreateUserResult createRole(UUID id) {
        //Preconditions.checkArgument(userRepository.findByLogin(id.toString()).isEmpty(), "Il existe déjà un rôle dont l’identifiant est " + id.toString());
        OreSiUser result = new OreSiUser();
        result.setLogin(id.toString());
        OreSiUserRole userRole = getUserRole(result);
        db.createRole(userRole);
        return new CreateUserResult(result.getId());
    }

    public OreSiUser deleteUserRightSuperadmin(UUID userId) {
        resetRole();
        final OreSiUser oreSiUser = getOreSiUser(userId);
        OreSiUserRole roleToModify = getUserRole(userId);
        OreSiSuperAdminRole roleToRevoke = OreSiRole.superAdmin();
        db.removeUserInRole(roleToModify, new OreSiRoleToBeGranted() {
            @Override
            public String getAsSqlRole() {
                return OreSiSuperAdminRole.SUPER_ADMIN.getAsSqlRole();
            }
        });
        return userRepository.findById(userId);
    }
    public OreSiUser addUserRightSuperadmin(UUID userId) {
        resetRole();
        final OreSiUser oreSiUser = getOreSiUser(userId);
        OreSiUserRole roleToModify = getUserRole(userId);
        OreSiSuperAdminRole roleToAdd = OreSiRole.superAdmin();
        db.addUserInRole(roleToModify, new OreSiRoleToBeGranted() {
            @Override
            public String getAsSqlRole() {
                return OreSiSuperAdminRole.SUPER_ADMIN.getAsSqlRole();
            }
        });
        return userRepository.findById(userId);
    }

    public OreSiUser deleteUserRightCreateApplication(UUID userId, String applicationPattern) {
        resetRole();
        final OreSiUser oreSiUser = getOreSiUser(userId);
        OreSiUserRole roleToModify = getUserRole(userId);
        oreSiUser.getAuthorizations().remove(applicationPattern);
        OreSiApplicationCreatorRole roleToAdd = OreSiRole.applicationCreator();
        db.removeUserInRole(roleToModify, roleToAdd);
        final String expression = oreSiUser.getAuthorizations().stream()
                .map(s -> String.format("%s", s))
                .collect(Collectors.joining("|", "name ~ '(", ")'"));
        final SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.applicationCreator().getAsSqlRole(), userId.toString()),
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                new OreSiRole() {
                    @Override
                    public String getAsSqlRole() {
                        return userId.toString();
                    }
                },
                expression,
                null
        );
        if(oreSiUser.getAuthorizations().isEmpty()){
            db.dropPolicy(sqlPolicy);
        }else{
            db.createPolicy(sqlPolicy);
        }

        setRoleForClient();
        if(!Strings.isNullOrEmpty(applicationPattern)){
            userRepository.updateAuthorizations(userId, oreSiUser.getAuthorizations());
            userRepository.flush();
        }
        resetRole();
        return userRepository.findById(userId);
    }

    public OreSiUser addUserRightCreateApplication(UUID userId, String applicationPattern) {
        resetRole();
        final OreSiUser oreSiUser = getOreSiUser(userId);
        OreSiUserRole roleToModify = getUserRole(userId);
        oreSiUser.getAuthorizations().add(applicationPattern);
        OreSiApplicationCreatorRole roleToAdd = OreSiRole.applicationCreator();
        db.addUserInRole(roleToModify, roleToAdd);
        final String expression = oreSiUser.getAuthorizations().stream()
                .map(s -> String.format("%s", s))
                .collect(Collectors.joining("|", "name ~ '(", ")'"));
        final SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", OreSiRole.applicationCreator().getAsSqlRole(), userId.toString()),
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                List.of(SqlPolicy.Statement.ALL),
                new OreSiRole() {
                    @Override
                    public String getAsSqlRole() {
                        return userId.toString();
                    }
                },
                expression,
                null
        );
        db.createPolicy(sqlPolicy);
        setRoleForClient();
        if(!Strings.isNullOrEmpty(applicationPattern)){
            userRepository.updateAuthorizations(userId, oreSiUser.getAuthorizations());
            userRepository.flush();
        }
        resetRole();
        return userRepository.findById(userId);
    }

    public void removeUser(UUID userId) {
        OreSiUser oreSiUser = getOreSiUser(userId);
        boolean deleted = userRepository.delete(userId);
        if (deleted) {
            OreSiUserRole userRoleToDelete = getUserRole(oreSiUser);
            db.dropRole(userRoleToDelete);
        }
    }

    private OreSiUser getOreSiUser(UUID userId) {
        return userRepository.tryFindById(userId)
                .orElseThrow(() -> new IllegalArgumentException("l'utilisateur " + userId + " n'existe pas en base"));
    }

    public OreSiUserRole getUserRole(UUID userId) {
        OreSiUser user = getOreSiUser(userId);
        return getUserRole(user);
    }

    public boolean hasRole(UUID uuid, OreSiRole role) {
        final CurrentUserRoles rolesForRole = userRepository.getRolesForRole(uuid.toString());
        return rolesForRole.getMemberOf().contains(role.getAsSqlRole());
    }

        public boolean hasRole(OreSiRole role) {
        setRoleForClient();
        final CurrentUserRoles currentUserRoles = userRepository.getRolesForCurrentUser();
        setRoleAdmin();
        return currentUserRoles.isSuper() ||
                currentUserRoles.getCurrentUser().equals(role.getAsSqlRole()) ||
                currentUserRoles.getMemberOf().contains(role.getAsSqlRole());
    }

    public boolean isSuperAdmin() {
        return hasRole(OreSiRole.superAdmin());
    }

    public OreSiUserRole getUserRole(OreSiUser user) {
        return OreSiUserRole.forUser(user);
    }

    public List<LoginResult> getAuthorizations() {
        if(hasRole(OreSiRole.superAdmin())){
           return userRepository.findAll().stream()
                    .map(oreSiUser -> new LoginResult(
                            oreSiUser.getId(),
                            oreSiUser.getLogin(),
                            oreSiUser.getAuthorizations().size()>0,
                            hasRole(oreSiUser.getId(),OreSiRole.superAdmin()),
                            oreSiUser.getAuthorizations()))
                    .collect(Collectors.toList());
        }else if(hasRole(OreSiRole.applicationCreator())) {
            return userRepository.findAll().stream()
                    .map(oreSiUser -> new LoginResult(
                            oreSiUser.getId(),
                            oreSiUser.getLogin(),
                            oreSiUser.getAuthorizations().size() > 0,
                            hasRole(oreSiUser.getId(), OreSiRole.applicationCreator()),
                            oreSiUser.getAuthorizations()))
                    .collect(Collectors.toList());
        }else{
            throw new NotSuperAdminException();
        }
    }

    public OreSiUser getByIdOrLogin(String userIdOrLogin) {
         return userRepository.findByLogin(userIdOrLogin).orElseGet(()->userRepository.findById(UUID.fromString(userIdOrLogin)));
    }
}