package fr.inra.oresing;

import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;

import java.util.UUID;

public interface OreSiRequestClient {

    UUID id();

    OreSiRoleToAccessDatabase role();
}
