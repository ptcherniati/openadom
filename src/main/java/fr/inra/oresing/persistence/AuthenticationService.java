package fr.inra.oresing.persistence;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.common.base.Preconditions;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.OreSiApplicationCreatorRole;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import fr.inra.oresing.rest.CreateUserResult;
import fr.inra.oresing.rest.LoginResult;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Predicate;

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
     */
    public void setRoleForClient() {
        OreSiRoleToAccessDatabase roleToAccessDatabase = request.getRequestClient().getRole();
        setRole(roleToAccessDatabase);
    }

    /**
     * Prend le role du superadmin qui a le droit de tout faire
     */
    public void setRoleAdmin() {
        setRole(OreSiRole.superAdmin());
    }

    /**
     * Prend le role du user passe en parametre, les requetes suivant ne pourra
     * pas faire des choses que l'utilisateur n'a pas le droit de faire
     */
    void setRole(OreSiRoleToAccessDatabase roleToAccessDatabase) {
        db.setRole(roleToAccessDatabase);
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
        return new LoginResult(oreSiUser.getId(), oreSiUser.getLogin(), authorizedForApplicationCreation);
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
        db.createRole(userRole);
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

    public void addUserRightCreateApplication(UUID userId) {
        OreSiUserRole roleToModify = getUserRole(userId);
        OreSiApplicationCreatorRole roleToAdd = OreSiRole.applicationCreator();
        db.addUserInRole(roleToModify, roleToAdd);
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

    public OreSiUserRole getUserRole(OreSiUser user) {
        return OreSiUserRole.forUser(user);
    }
}