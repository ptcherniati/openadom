package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.WithSqlIdentifier;

@FunctionalInterface
public interface
OreSiRole extends WithSqlIdentifier {

    static OreSiAnonymousRole anonymous() {
        return OreSiAnonymousRole.ANONYMOUS;
    }

    static OreSiopenAdomAdminRole openAdomAdmin() {
        return OreSiopenAdomAdminRole.openAdomAdmin;
    }

    static OreSiApplicationCreatorRole applicationCreator() {
        return OreSiApplicationCreatorRole.APPLICATION_CREATOR;
    }

    static OreSiRole applicationManagerOf(Application application) {
        return OreSiRightOnApplicationRole.adminOn(application);
    }
    static OreSiRole userManagerOf(Application application) {
        return OreSiRightOnApplicationRole.userAdminOn(application);
    }

    String getAsSqlRole();

    @Override
    default String getSqlIdentifier() {
        return "\"" + getAsSqlRole() + "\"";
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    default String toSqlCreaterole(String comment) {
        return """
                CREATE ROLE %1$s;
                            COMMENT ON ROLE  %1$s IS %2$s;""".formatted(getSqlIdentifier(), quote(comment));
    }

    default String addUserInRoleSql(final OreSiRoleWeCanGrantOtherRolesTo roleToModify, final boolean withAdminOption) {
        final String withAdminOptionClause = withAdminOption ? " WITH ADMIN OPTION" : "";
        return(OreSiRole.openAdomAdmin().getAsSqlRole().equals(getAsSqlRole()) ?
                """
                GRANT %1$s TO %2$s%3$s;
                GRANT %1$s TO %2$s WITH INHERIT TRUE;
                """ :
                """
                GRANT %1$s TO %2$s%3$s;
                GRANT %1$s TO %2$s WITH INHERIT TRUE;
                GRANT %1$s TO "openAdomAdmin" WITH ADMIN OPTION;
                GRANT %1$s TO "openAdomAdmin" WITH INHERIT TRUE;
                """)
                .formatted(
                        getSqlIdentifier(),
                        roleToModify.getSqlIdentifier(),
                        withAdminOptionClause
                );
    }
}
