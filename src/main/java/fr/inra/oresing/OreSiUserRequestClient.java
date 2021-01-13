package fr.inra.oresing;

import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.Value;

import java.util.UUID;

@Value
public class OreSiUserRequestClient implements OreSiRequestClient {

    UUID id;

    OreSiUserRole role;

    public static OreSiUserRequestClient of(UUID userId, OreSiUserRole userRole) {
        OreSiUserRequestClient newRequestClient = new OreSiUserRequestClient(userId, userRole);
        return newRequestClient;
    }
}
