package fr.inra.oresing.persistence.roles;

public enum OreSiSuperAdminRole implements OreSiRoleToAccessDatabase {

    SUPER_ADMIN;

    @Override
    public String getAsSqlRole() {
        return "superadmin";
    }
}
