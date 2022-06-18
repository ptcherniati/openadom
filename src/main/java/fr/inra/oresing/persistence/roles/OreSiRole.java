package fr.inra.oresing.persistence.roles;

import fr.inra.oresing.persistence.WithSqlIdentifier;

@FunctionalInterface
public interface
OreSiRole extends WithSqlIdentifier {

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

    @Override
    default String getSqlIdentifier() {
        return "\"" + getAsSqlRole() + "\"";
    }
}