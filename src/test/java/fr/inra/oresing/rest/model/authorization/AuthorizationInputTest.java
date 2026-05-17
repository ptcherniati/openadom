package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link AuthorizationInput}.
 * Aucun contexte Spring — instanciation directe.
 */
@Tag("domain.model")
@DisplayName("AuthorizationInput — opérations et portée temporelle")
class AuthorizationInputTest {

    // ─── constructeur vide ───────────────────────────────────────────────────

    @Test
    @DisplayName("Constructeur vide : valeurs par défaut")
    void defaultConstructor() {
        AuthorizationInput input = new AuthorizationInput();
        assertThat(input.getOperationTypes()).isEmpty();
        assertThat(input.getRequiredAuthorizations()).isEmpty();
    }

    // ─── constructeur avec publication ─────────────────────────────────────

    @Nested
    @DisplayName("Constructeur avec publication → ajoute depot, delete, extraction")
    class ConstructorPublicationTest {

        @Test
        @DisplayName("publication entraîne depot + delete + extraction dans le constructeur")
        void publicationAddsDepotDeleteExtraction() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.publication)
            );
            assertThat(input.getOperationTypes())
                    .contains(OperationType.publication, OperationType.depot,
                              OperationType.delete, OperationType.extraction);
        }

        @Test
        @DisplayName("depot seul entraîne extraction dans le constructeur")
        void depotAddsExtraction() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.depot)
            );
            assertThat(input.getOperationTypes())
                    .contains(OperationType.depot, OperationType.extraction);
            assertThat(input.getOperationTypes()).doesNotContain(OperationType.delete);
        }

        @Test
        @DisplayName("delete seul entraîne extraction dans le constructeur")
        void deleteAddsExtraction() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.delete)
            );
            assertThat(input.getOperationTypes())
                    .contains(OperationType.delete, OperationType.extraction);
        }

        @Test
        @DisplayName("extraction seule reste extraction seule dans le constructeur")
        void extractionOnly() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.extraction)
            );
            assertThat(input.getOperationTypes()).containsExactly(OperationType.extraction);
        }
    }

    // ─── setOperationTypes ──────────────────────────────────────────────────

    @Nested
    @DisplayName("setOperationTypes")
    class SetOperationTypesTest {

        @Test
        @DisplayName("setOperationTypes(publication) ajoute depot + extraction")
        void setPublicationAddsDepotExtraction() {
            AuthorizationInput input = new AuthorizationInput();
            input.setOperationTypes(EnumSet.of(OperationType.publication));
            assertThat(input.getOperationTypes())
                    .contains(OperationType.publication, OperationType.depot, OperationType.extraction);
        }

        @Test
        @DisplayName("setOperationTypes(depot) ajoute extraction")
        void setDepotAddsExtraction() {
            AuthorizationInput input = new AuthorizationInput();
            input.setOperationTypes(EnumSet.of(OperationType.depot));
            assertThat(input.getOperationTypes())
                    .contains(OperationType.depot, OperationType.extraction);
        }

        @Test
        @DisplayName("setOperationTypes(extraction) ne change rien")
        void setExtractionOnly() {
            AuthorizationInput input = new AuthorizationInput();
            input.setOperationTypes(EnumSet.of(OperationType.extraction));
            assertThat(input.getOperationTypes()).containsExactly(OperationType.extraction);
        }
    }

    // ─── setTimeScope ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("setTimeScope")
    class SetTimeScopeTest {

        @Test
        @DisplayName("setTimeScope avec format ISO yyyy-MM-dd et fromDay/toDay")
        void setTimeScopeLocalDate() {
            AuthorizationInput input = new AuthorizationInput();
            Map<String, String> dates = new HashMap<>();
            dates.put(AuthorizationInput.FORMAT, "yyyy-MM-dd");
            dates.put(AuthorizationInput.FROM_DAY, "2020-01-01");
            dates.put(AuthorizationInput.TO_DAY, "2022-12-31");
            input.setTimeScope(dates);
            assertThat(input.getTimeScope()).isNotNull();
        }

        @Test
        @DisplayName("setTimeScope sans format → timeScope null")
        void setTimeScopeNoFormat() {
            AuthorizationInput input = new AuthorizationInput();
            input.setTimeScope(new HashMap<>());
            assertThat(input.getTimeScope()).isNull();
        }

        @Test
        @DisplayName("setTimeScope avec format ISO yyyy-MM-dd HH:mm:ss et LocalDateTime")
        void setTimeScopeLocalDateTime() {
            AuthorizationInput input = new AuthorizationInput();
            Map<String, String> dates = new HashMap<>();
            dates.put(AuthorizationInput.FORMAT, "yyyy-MM-dd HH:mm:ss");
            dates.put(AuthorizationInput.FROM_DAY, "2020-01-01 00:00:00");
            dates.put(AuthorizationInput.TO_DAY, "2022-12-31 23:59:59");
            input.setTimeScope(dates);
            assertThat(input.getTimeScope()).isNotNull();
        }
    }

    // ─── withRestrictionWithDependants ──────────────────────────────────────

    @Nested
    @DisplayName("withRestrictionWithDependants")
    class WithRestrictionTest {

        @Test
        @DisplayName("extraction reste extraction pour un dataName non versionné")
        void extractionRemainsExtraction() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.extraction)
            );
            AuthorizationInput result = input.withRestrictionWithDependants("myData", __ -> false);
            assertThat(result.getOperationTypes()).contains(OperationType.extraction);
        }

        @Test
        @DisplayName("depot avec versionning ajoute publication, delete, extraction")
        void depotWithVersionningAddsAll() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.depot)
            );
            AuthorizationInput result = input.withRestrictionWithDependants("myData", __ -> true);
            assertThat(result.getOperationTypes())
                    .contains(OperationType.depot, OperationType.publication,
                              OperationType.delete, OperationType.extraction);
        }

        @Test
        @DisplayName("depot sans versionning ajoute publication et extraction mais pas delete")
        void depotWithoutVersionningNoDelete() {
            AuthorizationInput input = new AuthorizationInput(
                    new HashMap<>(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.depot)
            );
            AuthorizationInput result = input.withRestrictionWithDependants("myData", __ -> false);
            assertThat(result.getOperationTypes())
                    .contains(OperationType.depot, OperationType.publication, OperationType.extraction);
        }

        @Test
        @DisplayName("requiredAuthorizations et timeScope sont préservés")
        void preservesRequiredAuthorizationsAndTimeScope() {
            Map<String, List<Ltree>> requiredAuths = new HashMap<>();
            LocalDateTimeRange timeScope = LocalDateTimeRange.always();
            AuthorizationInput input = new AuthorizationInput(
                    requiredAuths,
                    timeScope,
                    EnumSet.of(OperationType.extraction)
            );
            AuthorizationInput result = input.withRestrictionWithDependants("myData", __ -> false);
            assertThat(result.getRequiredAuthorizations()).isSameAs(requiredAuths);
            assertThat(result.getTimeScope()).isEqualTo(timeScope);
        }
    }
}
