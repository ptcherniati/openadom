package fr.inra.oresing.domain.repository.authorization.role;

public enum OreSiopenAdomAdminRole implements OreSiRoleToAccessDatabase {

    openAdomAdmin;

    @Override
    public String getAsSqlRole() {
        return "openAdomAdmin";
    }
}
