package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.authorization.DomainUserDetails;

/**
 * Identité domaine d'un utilisateur non encore entré dans une session applicative complète.
 * Remplace l'ancienne dépendance à {@code org.springframework.security.core.userdetails.UserDetails}.
 */
public sealed interface NotConnectedUser extends DomainUserDetails
        permits NotConnectedAuthentifiedActiveUser, NotConnectedAuthentifiedActiveUserNotSignedCharte, NotConnectedAuthentifiedClosedUser, NotConnectedAuthentifiedIdleUser, NotConnectedAuthentifiedMissingPasswordUser, NotConnectedAuthentifiedPendingUser, NotConnectedUnauthentifiedUser, NotConnectedUnauthentifiedUserForCreate {


    @Override
    default String getPassword() {
        return "";
    }

    @Override
    default String getUsername() {
        return "";
    }
}