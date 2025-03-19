package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface NotConnectedUser permits
        NotConnectedAuthentifiedActiveUser,
        NotConnectedAuthentifiedPendingUser,
        NotConnectedAuthentifiedIdleUser,
        NotConnectedAuthentifiedClosedUser,
        NotConnectedAuthentifiedActiveUserNotSignedCharte,
        NotConnectedAuthentifiedMissingPasswordUser,
        NotConnectedUnauthentifiedUser{
}
