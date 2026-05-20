package fr.inra.oresing.domain.repository.user.file;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<OreSiUser> findByLogin(String login);

    Optional<OreSiUser> findByLoginOrId(String loginOrId);

    CurrentUserRoles getRolesForCurrentUser();

    OreSiUser findById(UUID id);

    Optional<OreSiUser> findByLoginAndEmail(String login, String email);

    /**
     * Lookup d'un user par email seul ( case-insensitive cote SQL ) .
     * Utilise par le flux "mot de passe oublie" step 1 ou seul l'email
     * est saisi par l'utilisateur dans la modal de reinitialisation .
     *
     * @param email adresse email a rechercher ; normalisation ( trim +
     *              lowercase ) attendue par le caller
     * @return user matchant ou {@link Optional#empty()} si introuvable
     */
    Optional<OreSiUser> findByEmail(String email);
}
