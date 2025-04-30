package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public sealed interface NotConnectedUser extends UserDetails
        permits NotConnectedAuthentifiedActiveUser, NotConnectedAuthentifiedActiveUserNotSignedCharte, NotConnectedAuthentifiedClosedUser, NotConnectedAuthentifiedIdleUser, NotConnectedAuthentifiedMissingPasswordUser, NotConnectedAuthentifiedPendingUser, NotConnectedUnauthentifiedUser, NotConnectedUnauthentifiedUserForCreate {

    @Override
    default Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    default String getPassword() {
        return "";
    }

    @Override
    default String getUsername() {
        return "";
    }
}
