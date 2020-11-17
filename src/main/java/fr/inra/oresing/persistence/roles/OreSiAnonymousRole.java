package fr.inra.oresing.persistence.roles;

public enum OreSiAnonymousRole implements OreSiRoleToAccessDatabase {

    ANONYMOUS;

    @Override
    public String getAsSqlRole() {
        return "anonymous";
    }
}
