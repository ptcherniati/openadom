package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            RightsRequestResult result = new RightsRequestResult(rr, parsedAuth, "admin", "admin@example.com");

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
        @DisplayName("userEmail et treatedByLogin sont propagés depuis le constructeur")
        void userEmailAndTreatedByLoginPropagated() {
            RightsRequest rr = buildRightsRequest();
            RightsRequestResult result = new RightsRequestResult(rr, Map.of(), "gestionnaire", "user@org.fr");
            assertThat(result.getUserEmail()).isEqualTo("user@org.fr");
            assertThat(result.getTreatedByLogin()).isEqualTo("gestionnaire");
        }

        @Test
        @DisplayName("champs traitement mappés depuis RightsRequest")
        void treatmentFieldsMapped() {
            RightsRequest rr = buildRightsRequest();
            UUID manager = UUID.randomUUID();
            rr.setTreatedBy(manager);
            rr.setTreatmentDecision("APPROVED");
            rr.setTreatmentComment("OK");
            rr.setTreatmentMailSubject("Demande approuvée");
            rr.setTreatmentMailBody("Bonjour, votre demande a été approuvée.");
            rr.setLinkedAuthorizationIds(List.of(UUID.randomUUID(), UUID.randomUUID()));

            RightsRequestResult result = new RightsRequestResult(rr, Map.of(), "admin", null);

            assertThat(result.getTreatedBy()).isEqualTo(manager);
            assertThat(result.getTreatmentDecision()).isEqualTo("APPROVED");
            assertThat(result.getTreatmentComment()).isEqualTo("OK");
            assertThat(result.getTreatmentMailSubject()).isEqualTo("Demande approuvée");
            assertThat(result.getTreatmentMailBody()).isEqualTo("Bonjour, votre demande a été approuvée.");
            assertThat(result.getLinkedAuthorizationIds()).hasSize(2);
        }

        @Test
        @DisplayName("champs traitement null quand non renseignés dans RightsRequest")
        void treatmentFieldsNullByDefault() {
            RightsRequest rr = buildRightsRequest();
            RightsRequestResult result = new RightsRequestResult(rr, Map.of(), null, null);
            assertThat(result.getTreatedBy()).isNull();
            assertThat(result.getTreatmentDecision()).isNull();
            assertThat(result.getTreatmentComment()).isNull();
            assertThat(result.getTreatmentMailSubject()).isNull();
            assertThat(result.getTreatmentMailBody()).isNull();
            assertThat(result.getLinkedAuthorizationIds()).isNull();
        }

        @Test
        @DisplayName("setted=false est bien conservé")
        void settedFalse() {
            RightsRequest rr = buildRightsRequest();
            rr.setSetted(false);
            RightsRequestResult result = new RightsRequestResult(rr, Map.of(), null, null);
            assertThat(result.isSetted()).isFalse();
        }

        @Test
        @DisplayName("le rightsRequest (authorizations parsées) est correctement stocké")
        void parsedAuthorizationStored() {
            RightsRequest rr = buildRightsRequest();
            Map<String, List<fr.inra.oresing.rest.model.authorization.AuthorizationParsed>> auth =
                    new java.util.HashMap<>();
            auth.put("scope", List.of());
            RightsRequestResult result = new RightsRequestResult(rr, auth, null, null);
            assertThat(result.getRightsRequest()).containsKey("scope");
        }
    }

    // ─── TreatRightsRequestRequest ────────────────────────────────────────────

    @Nested
    @DisplayName("TreatRightsRequestRequest")
    class TreatRightsRequestRequestTest {

        @Test
        @DisplayName("record stocke les six champs")
        void fieldsStoredCorrectly() {
            UUID auth1 = UUID.randomUUID();
            TreatRightsRequestRequest req = new TreatRightsRequestRequest(
                    "APPROVED", List.of(auth1), "OK pour moi", "Objet mail", "Corps du mail", false);
            assertThat(req.status()).isEqualTo("APPROVED");
            assertThat(req.linkedAuthorizationIds()).containsExactly(auth1);
            assertThat(req.treatmentComment()).isEqualTo("OK pour moi");
            assertThat(req.mailSubject()).isEqualTo("Objet mail");
            assertThat(req.mailBody()).isEqualTo("Corps du mail");
            assertThat(req.suppressMail()).isFalse();
        }

        @Test
        @DisplayName("status REJECTED avec suppressMail=true")
        void rejectedWithSuppressMail() {
            TreatRightsRequestRequest req = new TreatRightsRequestRequest(
                    "REJECTED", List.of(), "Refusé", null, null, true);
            assertThat(req.status()).isEqualTo("REJECTED");
            assertThat(req.linkedAuthorizationIds()).isEmpty();
            assertThat(req.suppressMail()).isTrue();
        }

        @Test
        @DisplayName("record equality – deux instances identiques sont égales")
        void equality() {
            UUID id = UUID.randomUUID();
            TreatRightsRequestRequest a = new TreatRightsRequestRequest(
                    "APPROVED", List.of(id), "c", "s", "b", false);
            TreatRightsRequestRequest b = new TreatRightsRequestRequest(
                    "APPROVED", List.of(id), "c", "s", "b", false);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    // ─── RightsRequestInfos ───────────────────────────────────────────────────

    @Nested
    @DisplayName("RightsRequestInfos")
    class RightsRequestInfosTest {

        @Test
        @DisplayName("valeurs par défaut à la construction")
        void defaultValues() {
            RightsRequestInfos infos = new RightsRequestInfos();
            assertThat(infos.getUuids()).isEmpty();
            assertThat(infos.getAuthorizations()).isEmpty();
            assertThat(infos.getLocale()).isEqualTo("fr_FR");
            assertThat(infos.getOffset()).isZero();
            assertThat(infos.getLimit()).isNull();
            assertThat(infos.getFieldFilters()).isEmpty();
            assertThat(infos.getSetted()).isNull();
        }

        @Test
        @DisplayName("affectation des champs setter/getter")
        void settersAndGetters() {
            RightsRequestInfos infos = new RightsRequestInfos();
            UUID id = UUID.randomUUID();
            infos.setUuids(new LinkedHashSet<>(Set.of(id)));
            infos.setLocale("en_US");
            infos.setOffset(10L);
            infos.setLimit(50L);
            infos.setSetted(Boolean.TRUE);

            assertThat(infos.getUuids()).containsExactly(id);
            assertThat(infos.getLocale()).isEqualTo("en_US");
            assertThat(infos.getOffset()).isEqualTo(10L);
            assertThat(infos.getLimit()).isEqualTo(50L);
            assertThat(infos.getSetted()).isTrue();
        }

        @Test
        @DisplayName("toString ne lève pas d'exception")
        void toStringDoesNotThrow() {
            assertThat(new RightsRequestInfos().toString()).isNotBlank();
        }

        // ── FieldFilters ──────────────────────────────────────────────────────

        @Nested
        @DisplayName("RightsRequestInfos.FieldFilters")
        class FieldFiltersTest {

            @Test
            @DisplayName("constructeur complet stocke tous les champs")
            void fullConstructor() {
                RightsRequestInfos.IntervalValues iv =
                        new RightsRequestInfos.IntervalValues("2024-01-01", "2024-12-31");
                RightsRequestInfos.FieldFilters ff =
                        new RightsRequestInfos.FieldFilters("myField", ".*val.*", "date", "dd/MM/yyyy", iv, true);
                assertThat(ff.field).isEqualTo("myField");
                assertThat(ff.filter).isEqualTo(".*val.*");
                assertThat(ff.type).isEqualTo("date");
                assertThat(ff.format).isEqualTo("dd/MM/yyyy");
                assertThat(ff.intervalValues).isSameAs(iv);
                assertThat(ff.isRegExp).isTrue();
            }

            @Test
            @DisplayName("constructeur par défaut : isRegExp=false")
            void defaultConstructor() {
                RightsRequestInfos.FieldFilters ff = new RightsRequestInfos.FieldFilters();
                assertThat(ff.isRegExp).isFalse();
                assertThat(ff.field).isNull();
            }

            @Test
            @DisplayName("isNumeric() vrai si type=numeric")
            void isNumeric() {
                RightsRequestInfos.FieldFilters ff = new RightsRequestInfos.FieldFilters();
                ff.type = "numeric";
                assertThat(ff.isNumeric()).isTrue();
                ff.type = "date";
                assertThat(ff.isNumeric()).isFalse();
            }

            @Test
            @DisplayName("isDate() vrai si type=date")
            void isDate() {
                RightsRequestInfos.FieldFilters ff = new RightsRequestInfos.FieldFilters();
                ff.type = "date";
                assertThat(ff.isDate()).isTrue();
                ff.type = "numeric";
                assertThat(ff.isDate()).isFalse();
            }

            @Test
            @DisplayName("getFilter() retourne null si filter est null")
            void getFilterNull() {
                RightsRequestInfos.FieldFilters ff = new RightsRequestInfos.FieldFilters();
                assertThat(ff.getFilter()).isNull();
            }
        }

        // ── IntervalValues ────────────────────────────────────────────────────

        @Nested
        @DisplayName("RightsRequestInfos.IntervalValues")
        class IntervalValuesTest {

            @Test
            @DisplayName("constructeur complet stocke from et to")
            void fullConstructor() {
                RightsRequestInfos.IntervalValues iv =
                        new RightsRequestInfos.IntervalValues("2024-01-01", "2024-12-31");
                assertThat(iv.from).isEqualTo("2024-01-01");
                assertThat(iv.to).isEqualTo("2024-12-31");
            }

            @Test
            @DisplayName("constructeur par défaut : from et to sont null")
            void defaultConstructor() {
                RightsRequestInfos.IntervalValues iv = new RightsRequestInfos.IntervalValues();
                assertThat(iv.from).isNull();
                assertThat(iv.to).isNull();
            }
        }
    }
}