package fr.inra.oresing.domain.repository.authorization.role;

public enum OreSiPublicRole implements OreSiRoleToAccessDatabase {

    PUBLIC;

    /** UUID racine du rôle public — extrait de {@code SqlSchemaForApplication.PUBLIC_UUID}. */
    public static final String PUBLIC_ROLE_ID = "9032ffe5-bfc1-453d-814e-287cd678484a";

    @Override
    public String getAsSqlRole() {
        return PUBLIC_ROLE_ID;
    }
}