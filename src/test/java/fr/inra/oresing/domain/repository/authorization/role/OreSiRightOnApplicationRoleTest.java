package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.domain.application.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les fabriques statiques de {@link OreSiRightOnApplicationRole}.
 */
@Tag("domain.model")
@DisplayName("OreSiRightOnApplicationRole – static factories")
class OreSiRightOnApplicationRoleTest {

    private static Application app(String name) {
        Application a = new Application();
        a.setId(UUID.randomUUID());
        a.setName(name);
        return a;
    }

    @Test
    @DisplayName("adminOn(application) crée un rôle APPLICATION_MANAGER")
    void adminOn() {
        Application a = app("testApp");
        OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.adminOn(a);
        assertThat(role.applicationId()).isEqualTo(a.getId());
        assertThat(role.profile()).isEqualTo(OreSiRightOnApplicationRole.APPLICATION_MANAGER);
        assertThat(role.authorizationId()).isNull();
    }

    @Test
    @DisplayName("userAdminOn(application) crée un rôle USER_MANAGER")
    void userAdminOn() {
        Application a = app("testApp");
        OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.userAdminOn(a);
        assertThat(role.applicationId()).isEqualTo(a.getId());
        assertThat(role.profile()).isEqualTo(OreSiRightOnApplicationRole.USER_MANAGER);
        assertThat(role.authorizationId()).isNull();
    }

    @Test
    @DisplayName("PUBLIC() crée le rôle public avec UUID fixe")
    void publicRole() {
        OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.PUBLIC();
        assertThat(role.profile()).isEqualTo(OreSiRightOnApplicationRole.PUBLIC);
        assertThat(role.applicationId()).isNull();
        assertThat(role.authorizationId()).isNotNull();
    }

    @Test
    @DisplayName("readerOn(application) crée un rôle reader")
    void readerOn() {
        Application a = app("readApp");
        OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.readerOn(a);
        assertThat(role.applicationId()).isEqualTo(a.getId());
        assertThat(role.profile()).isEqualTo("reader");
        assertThat(role.authorizationId()).isNull();
    }

    @Test
    @DisplayName("writerOn(application) crée un rôle writer")
    void writerOn() {
        Application a = app("writeApp");
        OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.writerOn(a);
        assertThat(role.applicationId()).isEqualTo(a.getId());
        assertThat(role.profile()).isEqualTo("writer");
        assertThat(role.authorizationId()).isNull();
    }

    @Test
    @DisplayName("managementRole(application, uuid) crée un rôle mgt_xxx avec authorizationId")
    void managementRole() {
        Application a = app("mgtApp");
        UUID mgtId = UUID.randomUUID();
        OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.managementRole(a, mgtId);
        assertThat(role.applicationId()).isEqualTo(a.getId());
        assertThat(role.profile()).startsWith("mgt_");
        assertThat(role.authorizationId()).isEqualTo(mgtId);
    }

    @Test
    @DisplayName("record equals et hashCode cohérents")
    void equalsHashCode() {
        Application a = app("app");
        OreSiRightOnApplicationRole r1 = OreSiRightOnApplicationRole.adminOn(a);
        OreSiRightOnApplicationRole r2 = OreSiRightOnApplicationRole.adminOn(a);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("Constants: PUBLIC et APPLICATION_MANAGER et USER_MANAGER non nuls")
    void constants() {
        assertThat(OreSiRightOnApplicationRole.APPLICATION_MANAGER).isEqualTo("applicationManager");
        assertThat(OreSiRightOnApplicationRole.USER_MANAGER).isEqualTo("userManager");
        assertThat(OreSiRightOnApplicationRole.PUBLIC).isEqualTo("public");
    }
}
