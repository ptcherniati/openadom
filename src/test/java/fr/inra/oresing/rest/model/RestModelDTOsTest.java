package fr.inra.oresing.rest.model;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.data.DataRowResult;
import fr.inra.oresing.rest.model.data.UserDescriptionResult;
import fr.inra.oresing.rest.model.reference.GetReferenceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires des DTOs du package rest.model.
 * Aucun contexte Spring.
 */
@DisplayName("REST model DTOs")
@Tag("domain.model")
class RestModelDTOsTest {

    // ---------------------------------------------------------
    // UserDescriptionResult
    // ---------------------------------------------------------

    @Nested
    @DisplayName("UserDescriptionResult")
    class UserDescriptionResultTest {

        @Test
        @DisplayName("record constructor stocke les champs")
        void recordConstructor() {
            UUID id = UUID.randomUUID();
            UserDescriptionResult r = new UserDescriptionResult(id, "alice", "alice@example.com");
            assertThat(r.id()).isEqualTo(id);
            assertThat(r.login()).isEqualTo("alice");
            assertThat(r.email()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("of(OreSiUser) mappe correctement les champs")
        void ofMapsUser() {
            OreSiUser user = new OreSiUser();
            user.setLogin("bob");
            user.setEmail("bob@example.com");

            UserDescriptionResult r = UserDescriptionResult.of(user);
            assertThat(r.id()).isEqualTo(user.getId());
            assertThat(r.login()).isEqualTo("bob");
            assertThat(r.email()).isEqualTo("bob@example.com");
        }
    }

    // ---------------------------------------------------------
    // ApplicationResult inner classes
    // ---------------------------------------------------------

    @Nested
    @DisplayName("ApplicationResult inner types")
    class ApplicationResultInnerTest {

        @Test
        @DisplayName("DataSynthesis setters/getters")
        void dataSynthesisSettersGetters() {
            ApplicationResult.DataSynthesis ds = new ApplicationResult.DataSynthesis();
            ds.setReferenceType("water_quality");
            ds.setLineCount(42);
            assertThat(ds.getReferenceType()).isEqualTo("water_quality");
            assertThat(ds.getLineCount()).isEqualTo(42);
        }

        @Test
        @DisplayName("RightsRequest record")
        void rightsRequestRecord() {
            ApplicationResult.RightsRequest rr = new ApplicationResult.RightsRequest(null);
            assertThat(rr.description()).isNull();
        }

        @Test
        @DisplayName("AdditionalFile record")
        void additionalFileRecord() {
            Set<String> fields = Set.of("field1", "field2");
            ApplicationResult.AdditionalFile af = new ApplicationResult.AdditionalFile(fields);
            assertThat(af.fields()).containsExactlyInAnyOrder("field1", "field2");
        }
    }

    // ---------------------------------------------------------
    // DataRowResult
    // ---------------------------------------------------------

    @Nested
    @DisplayName("DataRowResult")
    class DataRowResultTest {

        @Test
        @DisplayName("DEFAULT constant is 'default'")
        void defaultConstant() {
            assertThat(DataRowResult.DEFAULT).isEqualTo("default");
        }
    }

    // ---------------------------------------------------------
    // GetReferenceResult.ReferenceValue
    // ---------------------------------------------------------

    @Nested
    @DisplayName("GetReferenceResult.ReferenceValue")
    class ReferenceValueTest {

        @Test
        @DisplayName("commparingValue concatène hierarchicalKey et patternColumnName")
        void commparingValue() {
            GetReferenceResult.ReferenceValue rv = new GetReferenceResult.ReferenceValue(
                    "id1",
                    "pattern_col",
                    "root_key",
                    "nat_key",
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
            assertThat(rv.commparingValue()).isEqualTo("root_key_pattern_col");
        }

        @Test
        @DisplayName("getters délèguent aux champs du record Lombok @Value")
        void gettersViaRecord() {
            GetReferenceResult.ReferenceValue rv = new GetReferenceResult.ReferenceValue(
                    "myId", "patt", "hk", "nk",
                    Map.of(), Map.of(), Map.of()
            );
            assertThat(rv.getId()).isEqualTo("myId");
            assertThat(rv.getNaturalKey()).isEqualTo("nk");
            assertThat(rv.getHierarchicalKey()).isEqualTo("hk");
        }
    }

    // ---------------------------------------------------------
    // OreSiUser enum OreSiUserStates
    // ---------------------------------------------------------

    @Nested
    @DisplayName("OreSiUser.OreSiUserStates enum")
    class OreSiUserStatesTest {

        @Test
        void allValues() {
            assertThat(OreSiUser.OreSiUserStates.values())
                    .containsExactlyInAnyOrder(
                            OreSiUser.OreSiUserStates.idle,
                            OreSiUser.OreSiUserStates.active,
                            OreSiUser.OreSiUserStates.pending,
                            OreSiUser.OreSiUserStates.closed);
        }

        @Test
        void setAndGetState() {
            OreSiUser user = new OreSiUser();
            user.setAccountstate(OreSiUser.OreSiUserStates.active);
            assertThat(user.getAccountstate()).isEqualTo(OreSiUser.OreSiUserStates.active);
        }
    }
}