package fr.inra.oresing.domain.repository.authorization.role;

public enum OreSiAnonymousRole implements OreSiRoleToAccessDatabase {

    ANONYMOUS;

    @Override
    public String getAsSqlRole() {
        return "anonymous";
    }
}
