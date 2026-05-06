package fr.inra.oresing.domain.repository.authorization.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour OreSiRole et OreSiPublicRole.
 */
@Tag("core.config")
@DisplayName("OreSiRole / OreSiPublicRole – factories et helpers SQL")
class OreSiRoleTest {

    @Test
    @DisplayName("OreSiRole.anonymous() retourne ANONYMOUS")
    void anonymous() {
        OreSiRole role = OreSiRole.anonymous();
        assertThat(role.getAsSqlRole()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("OreSiRole.publicRole() retourne PUBLIC")
    void publicRole() {
        OreSiRole role = OreSiRole.publicRole();
        assertThat(role).isEqualTo(OreSiPublicRole.PUBLIC);
    }

    @Test
    @DisplayName("OreSiRole.openAdomAdmin() retourne openAdomAdmin")
    void openAdomAdmin() {
        OreSiRole role = OreSiRole.openAdomAdmin();
        assertThat(role.getAsSqlRole()).isEqualTo("openAdomAdmin");
    }

    @Test
    @DisplayName("OreSiRole.applicationCreator() retourne APPLICATION_CREATOR")
    void applicationCreator() {
        OreSiRole role = OreSiRole.applicationCreator();
        assertThat(role.getAsSqlRole()).isEqualTo("applicationCreator");
    }

    @Test
    @DisplayName("getSqlIdentifier() encadre le rôle entre guillemets")
    void getSqlIdentifier() {
        String id = OreSiPublicRole.PUBLIC.getSqlIdentifier();
        assertThat(id).startsWith("\"").endsWith("\"");
        assertThat(id).contains(OreSiPublicRole.PUBLIC.getAsSqlRole());
    }

    @Test
    @DisplayName("toSqlCreaterole() génère une instruction SQL CREATE ROLE")
    void toSqlCreaterole() {
        String sql = OreSiPublicRole.PUBLIC.toSqlCreaterole("Public role");
        assertThat(sql).containsIgnoringCase("CREATE ROLE");
        assertThat(sql).containsIgnoringCase("COMMENT ON ROLE");
    }

    @Test
    @DisplayName("addUserInRoleSql() génère une instruction SQL GRANT")
    void addUserInRoleSql() {
        OreSiRoleWeCanGrantOtherRolesTo roleToModify = new OreSiRoleWeCanGrantOtherRolesTo() {
            @Override
            public String getAsSqlRole() {
                return "testRole";
            }
        };
        String sql = OreSiPublicRole.PUBLIC.addUserInRoleSql(roleToModify, false);
        assertThat(sql).containsIgnoringCase("GRANT");
        assertThat(sql).contains("testRole");
    }

    @Test
    @DisplayName("OreSiPublicRole.PUBLIC.getAsSqlRole() ne renvoie pas null")
    void publicRoleAsSqlRole() {
        assertThat(OreSiPublicRole.PUBLIC.getAsSqlRole()).isNotBlank();
    }
}