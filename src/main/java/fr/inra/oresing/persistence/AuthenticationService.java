package fr.inra.oresing.persistence;

import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.OreSiApplicationCreatorRole;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SqlService db;

    @Autowired
    private OreSiApiRequestContext request;

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
     * @return l'objet OreSiUser contenant les informations sur l'utilisateur identifié
     */
    public OreSiUser login(String login, String password) throws Throwable {
        return userRepository.login(login, password);
    }

    /**
     * Permet de créer un nouvel utilisateur
     * @return l'objet OreSiUser qui vient d'être créé
     */
    public OreSiUser createUser(String login, String password) {
        OreSiUser result = new OreSiUser();
        result.setLogin(login);
        result.setPassword(password);
        userRepository.store(result);
        OreSiUserRole userRole = getUserRole(result);
        db.createRole(userRole);
        return result;
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
