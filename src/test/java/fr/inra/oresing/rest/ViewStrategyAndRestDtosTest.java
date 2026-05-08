package fr.inra.oresing.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour ViewStrategy, CreateUserRequest, CreateUserResult et AdditionalFileOrUUID.
 */
@Tag("domain.model")
@DisplayName("REST DTOs légers et ViewStrategy")
class ViewStrategyAndRestDtosTest {

    // ─── ViewStrategy ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ViewStrategy")
    class ViewStrategyTest {

        @Test
        @DisplayName("DISABLED.isEnabled() == false")
        void disabledIsNotEnabled() {
            assertThat(ViewStrategy.DISABLED.isEnabled()).isFalse();
            assertThat(ViewStrategy.DISABLED.isRecreationOnDataUpdateRequired()).isFalse();
        }

        @Test
        @DisplayName("VIEW.isEnabled() == true, isRecreationOnDataUpdateRequired() == false")
        void viewIsEnabled() {
            assertThat(ViewStrategy.VIEW.isEnabled()).isTrue();
            assertThat(ViewStrategy.VIEW.isRecreationOnDataUpdateRequired()).isFalse();
        }

        @Test
        @DisplayName("TABLE.isEnabled() == true, isRecreationOnDataUpdateRequired() == true")
        void tableIsEnabledAndRequiresRecreation() {
            assertThat(ViewStrategy.TABLE.isEnabled()).isTrue();
            assertThat(ViewStrategy.TABLE.isRecreationOnDataUpdateRequired()).isTrue();
        }

        @Test
        @DisplayName("getError() retourne une exception non nulle")
        void getErrorReturnsException() {
            var error = ViewStrategy.getError(ViewStrategy.DISABLED);
            assertThat(error).isNotNull();
            assertThat(error.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("3 valeurs déclarées")
        void allValues() {
            assertThat(ViewStrategy.values()).hasSize(3);
        }
    }

    // ─── CreateUserRequest ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CreateUserRequest")
    class CreateUserRequestTest {

        @Test
        @DisplayName("setters et getters")
        void settersAndGetters() {
            CreateUserRequest req = new CreateUserRequest();
            req.setLogin("alice");
            req.setPassword("secret");
            req.setEmail("alice@example.com");
            req.setNewPassword("newSecret");
            req.setNewPasswordConfirm("newSecret");
            req.setVerificationKey("vkey");
            req.setCharte("charte-text");

            assertThat(req.getLogin()).isEqualTo("alice");
            assertThat(req.getPassword()).isEqualTo("secret");
            assertThat(req.getEmail()).isEqualTo("alice@example.com");
            assertThat(req.getNewPassword()).isEqualTo("newSecret");
            assertThat(req.getNewPasswordConfirm()).isEqualTo("newSecret");
            assertThat(req.getVerificationKey()).isEqualTo("vkey");
            assertThat(req.getCharte()).isEqualTo("charte-text");
        }

        @Test
        @DisplayName("toString() ne lance pas d'exception")
        void toStringDoesNotThrow() {
            CreateUserRequest req = new CreateUserRequest();
            req.setLogin("bob");
            assertThat(req.toString()).contains("bob");
        }
    }

    // ─── AdditionalFileOrUUID ────────────────────────────────────────────────

    @Nested
    @DisplayName("AdditionalFileOrUUID")
    class AdditionalFileOrUUIDTest {

        @Test
        @DisplayName("setters et getters")
        void settersAndGetters() {
            AdditionalFileOrUUID obj = new AdditionalFileOrUUID();
            UUID id = UUID.randomUUID();
            obj.setFileid(id);
            obj.setFields(java.util.Map.of("key", "value"));
            obj.setAssociates(java.util.Map.of());

            assertThat(obj.getFileid()).isEqualTo(id);
            assertThat(obj.getFields()).containsEntry("key", "value");
            assertThat(obj.getAssociates()).isEmpty();
        }

        @Test
        @DisplayName("fileid peut être null")
        void fileidCanBeNull() {
            AdditionalFileOrUUID obj = new AdditionalFileOrUUID();
            assertThat(obj.getFileid()).isNull();
        }
    }
}
