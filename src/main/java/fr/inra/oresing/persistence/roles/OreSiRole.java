package fr.inra.oresing.persistence.roles;

public interface OreSiRole {

    static OreSiAnonymousRole anonymous() {
        return OreSiAnonymousRole.ANONYMOUS;
    }

    static OreSiSuperAdminRole superAdmin() {
        return OreSiSuperAdminRole.SUPER_ADMIN;
    }

    static OreSiApplicationCreatorRole applicationCreator() {
        return OreSiApplicationCreatorRole.APPLICATION_CREATOR;
    }

    String getAsSqlRole();
}
