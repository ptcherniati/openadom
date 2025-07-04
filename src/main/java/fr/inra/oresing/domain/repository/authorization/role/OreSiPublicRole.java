package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.persistence.SqlSchemaForApplication;

public enum OreSiPublicRole implements OreSiRoleToAccessDatabase {

    PUBLIC;

    @Override
    public String getAsSqlRole() {
        return SqlSchemaForApplication.publicRoleId();
    }
}