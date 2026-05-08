package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des DTOs du package rest.model.rightsrequest.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("RightsRequest REST model DTOs")
class RightsRequestDTOsTest {

    // ─── CreateRightsRequestRequest ───────────────────────────────────────────

    @Nested
    @DisplayName("CreateRightsRequestRequest")
    class CreateRightsRequestRequestTest {

        @Test
        @DisplayName("record stocke les cinq champs")
        void fieldsStoredCorrectly() {
            UUID id = UUID.randomUUID();
            CreateRightsRequestRequest req = new CreateRightsRequestRequest(
                    id, Map.of("key", "val"), null, true, "comment");
            assertThat(req.id()).isEqualTo(id);
            assertThat(req.fields()).containsEntry("key", "val");
            assertThat(req.rightsRequest()).isNull();
            assertThat(req.setted()).isTrue();
            assertThat(req.comment()).isEqualTo("comment");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            UUID id = UUID.randomUUID();
            CreateRightsRequestRequest a = new CreateRightsRequestRequest(id, Map.of(), null, false, "c");
            CreateRightsRequestRequest b = new CreateRightsRequestRequest(id, Map.of(), null, false, "c");
            assertThat(a).isEqualTo(b);
        }
    }

    // ─── GetRightsRequestResult ───────────────────────────────────────────────

    @Nested
    @DisplayName("GetRightsRequestResult")
    class GetRightsRequestResultTest {

        @Test
        @DisplayName("record stocke les trois champs")
        void fieldsStoredCorrectly() {
            SortedSet<GetGrantableResult.User> users = new TreeSet<>(
                    java.util.Comparator.comparing(GetGrantableResult.User::label));
            users.add(new GetGrantableResult.User(UUID.randomUUID(), "alice"));
            RightRequestDescription desc = new RightRequestDescription(Map.of());
            GetRightsRequestResult r = new GetRightsRequestResult(users, List.of(), desc);
            assertThat(r.users()).containsExactlyInAnyOrderElementsOf(users);
            assertThat(r.rightsRequests()).isEmpty();
            assertThat(r.description()).isSameAs(desc);
        }

        @Test
        @DisplayName("record avec listes vides")
        void emptyLists() {
            GetRightsRequestResult r = new GetRightsRequestResult(
                    new TreeSet<>(), List.of(), new RightRequestDescription(Map.of()));
            assertThat(r.users()).isEmpty();
            assertThat(r.rightsRequests()).isEmpty();
        }
    }

    // ─── GetAdditionalFilesResult ─────────────────────────────────────────────

    @Nested
    @DisplayName("GetAdditionalFilesResult")
    class GetAdditionalFilesResultTest {

        @Test
        @DisplayName("record stocke les cinq champs")
        void fieldsStoredCorrectly() {
            GetAdditionalFilesResult r = new GetAdditionalFilesResult(
                    new TreeSet<>(), "myFile", List.of(), null, List.of("f1.csv"));
            assertThat(r.users()).isEmpty();
            assertThat(r.additionalFileName()).isEqualTo("myFile");
            assertThat(r.additionalBinaryFiles()).isEmpty();
            assertThat(r.description()).isNull();
            assertThat(r.fileNames()).containsExactly("f1.csv");
        }
    }

    // ─── RightsRequestResult ──────────────────────────────────────────────────

    @Nested
    @DisplayName("RightsRequestResult")
    class RightsRequestResultTest {

        private RightsRequest buildRightsRequest() {
            RightsRequest rr = new RightsRequest();
            rr.setApplication(UUID.randomUUID());
            rr.setUser(UUID.randomUUID());
            rr.setComment("test comment");
            rr.setRightsRequestForm(Map.of("field1", "value1"));
            rr.setSetted(true);
            rr.setCreationDate(LocalDateTime.of(2024, 1, 1, 10, 0));
            rr.setUpdateDate(LocalDateTime.of(2024, 6, 1, 12, 0));
            return rr;
        }

        @Test
        @DisplayName("constructeur depuis RightsRequest mappe correctement les champs")
        void constructorMapsFields() {
            RightsRequest rr = buildRightsRequest();
            Map<String, List<fr.inra.oresing.rest.model.authorization.AuthorizationParsed>> parsedAuth =
                    new java.util.HashMap<>();
            RightsRequestResult result = new RightsRequestResult(rr, parsedAuth);

            assertThat(result.getId()).isEqualTo(rr.getId());
            assertThat(result.getApplication()).isEqualTo(rr.getApplication());
            assertThat(result.getUser()).isEqualTo(rr.getUser());
            assertThat(result.getComment()).isEqualTo("test comment");
            assertThat(result.getRightsRequestForm()).containsEntry("field1", "value1");
            assertThat(result.isSetted()).isTrue();
            assertThat(result.getCreateDate()).isNotNull();
            assertThat(result.getUpdateDate()).isNotNull();
        }

        @Test
        @DisplayName("setted=false est bien conservé")
        void settedFalse() {
            RightsRequest rr = buildRightsRequest();
            rr.setSetted(false);
            RightsRequestResult result = new RightsRequestResult(rr, Map.of());
            assertThat(result.isSetted()).isFalse();
        }

        @Test
        @DisplayName("le rightsRequest (authorizations parsées) est correctement stocké")
        void parsedAuthorizationStored() {
            RightsRequest rr = buildRightsRequest();
            Map<String, List<fr.inra.oresing.rest.model.authorization.AuthorizationParsed>> auth =
                    new java.util.HashMap<>();
            auth.put("scope", List.of());
            RightsRequestResult result = new RightsRequestResult(rr, auth);
            assertThat(result.getRightsRequest()).containsKey("scope");
        }
    }
}
