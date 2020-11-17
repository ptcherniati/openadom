package fr.inra.oresing;

import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;

import java.util.UUID;

public interface OreSiRequestClient {

    UUID getId();

    OreSiRoleToAccessDatabase getRole();
}
