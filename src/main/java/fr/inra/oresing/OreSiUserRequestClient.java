package fr.inra.oresing;

import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;

import java.util.UUID;

public record OreSiUserRequestClient(UUID id, OreSiUserRole role) implements OreSiRequestClient {

    public static OreSiUserRequestClient of(final UUID userId, final OreSiUserRole userRole) {
        return new OreSiUserRequestClient(userId, userRole);
    }
}
