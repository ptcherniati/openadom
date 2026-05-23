package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.BasicComponent;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.BooleanChecker;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.DateChecker;
import fr.inra.oresing.domain.application.configuration.checker.FloatChecker;
import fr.inra.oresing.domain.application.configuration.checker.IntegerChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.application.configuration.checker.StringChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.RefsLinked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link FilterListVariablesExtractor} . Couvre les
 * trois sources qui contribuent au set {@code variables} retourné dans
 * le payload {@code /filters} :
 * <ul>
 *   <li>{@code FilterList.refsLinkeds[].components[]} ( ReferenceChecker )</li>
 *   <li>{@code ColumnDistinctValues.componentKey} ( FILTER_LIST / FILTER_TEXT )</li>
 *   <li>{@code componentDescriptions} avec checker Float / Integer / Date
 *       ( colonnes intervalle - absentes du payload mais affichées au front )</li>
 * </ul>
 *
 * <p>Garantit aussi le respect du filtre {@code isHiddenOrHasLangRestriction}
 * pour exclure les colonnes masquées par config YAML .
 */
@org.junit.jupiter.api.Tag("domain.model")
@DisplayName("FilterListVariablesExtractor - dérivation iso-source des columnKeys filtrables")
class FilterListVariablesExtractorTest {

    private static final String REF_TYPE = "t_soil";
    private static final String LOCALE = "fr";

    @Nested
    class FromFilterList {

        @Test
        @DisplayName("aplatit components[] de tous les FilterListNode")
        void aggregateComponentsAcrossLeaves() {
            FilterList fl = new FilterList("site_acbb", List.of(
                    leafWithComponents(List.of("site", "site_from")),
                    leafWithComponents(List.of("site_to", "site"))   // doublons -> dédoublonnés
            ));
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(fl), app, REF_TYPE, LOCALE);

            assertThat(result).containsExactly("site", "site_from", "site_to");
        }

        @Test
        @DisplayName("FilterList vide -> set vide ( pas d'erreur )")
        void emptyRefsLinkedsYieldsEmpty() {
            FilterList fl = new FilterList("ref_vide", List.of());
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(fl), app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("composants null / blancs ignorés")
        void nullAndBlankComponentsIgnored() {
            List<String> mixed = new java.util.ArrayList<>();
            mixed.add("ok");
            mixed.add(null);
            mixed.add("");
            mixed.add("   ");
            mixed.add("valid");
            FilterList fl = new FilterList("ref", List.of(leafWithComponents(mixed)));
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(fl), app, REF_TYPE, LOCALE);

            assertThat(result).containsExactly("ok", "valid");
        }
    }

    @Nested
    class FromColumnDistinctValues {

        @Test
        @DisplayName("ColumnDistinctValues -> componentKey ajouté")
        void addsComponentKey() {
            ColumnDistinctValues cdv = new ColumnDistinctValues(
                    "method_id", List.of("A", "B"), false, false);
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(cdv), app, REF_TYPE, LOCALE);

            assertThat(result).containsExactly("method_id");
        }

        @Test
        @DisplayName("ColumnDistinctValues avec componentKey null -> ignoré")
        void nullComponentKeyIgnored() {
            ColumnDistinctValues cdv = new ColumnDistinctValues(
                    null, List.of(), false, false);
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(cdv), app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class IntervalCheckersFromConfig {

        @Test
        @DisplayName("FloatChecker , IntegerChecker , DateChecker ajoutés depuis config")
        void allIntervalCheckersIncluded() {
            Application app = applicationWithComponents(Map.of(
                    "humidity", basicComponent("humidity",
                            new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker,
                                    Multiplicity.ONE, false, null, null)),
                    "count", basicComponent("count",
                            new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker,
                                    Multiplicity.ONE, false, null, null)),
                    "ts", basicComponent("ts",
                            new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker,
                                    Multiplicity.ONE, false, "yyyy-MM-dd", null, null, null))
            ));

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(), app, REF_TYPE, LOCALE);

            assertThat(result).containsExactlyInAnyOrder("count", "humidity", "ts");
        }

        @Test
        @DisplayName("colonnes non-interval ignorées ( StringChecker , BooleanChecker , ReferenceChecker )")
        void nonIntervalCheckersIgnored() {
            Application app = applicationWithComponents(Map.of(
                    "label", basicComponent("label",
                            new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker,
                                    Multiplicity.ONE, false, ".*")),
                    "active", basicComponent("active",
                            new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker,
                                    Multiplicity.ONE, false, true)),
                    "site", basicComponent("site",
                            new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                                    "site", Multiplicity.ONE, false, "site_acbb", false, false))
            ));

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(), app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("colonne avec checker null ignorée")
        void nullCheckerIgnored() {
            Application app = applicationWithComponents(Map.of(
                    "no_checker", basicComponent("no_checker", null)
            ));

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(), app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("colonne hidden -> exclue même si interval ( tag HiddenTag )")
        void hiddenIntervalExcluded() {
            BasicComponent hidden = new BasicComponent(
                    ComponentDescription.ComponentDescriptionType.BasicComponent,
                    "hidden_humidity",
                    null,
                    Set.of(Tag.HiddenTag.instance()),   // tag HIDDEN
                    null, null, List.of(), false, null,
                    new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker,
                            Multiplicity.ONE, false, null, null),
                    null);

            Application app = applicationWithComponents(Map.of("hidden_humidity", hidden));

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(), app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("colonne lang-restriction -> exclue si locale incompatible")
        void langRestrictionExcluded() {
            BasicComponent enOnly = new BasicComponent(
                    ComponentDescription.ComponentDescriptionType.BasicComponent,
                    "en_only_humidity",
                    null,
                    Set.of(),
                    null, null,
                    List.of(Locale.ENGLISH),  // restreint à anglais
                    false, null,
                    new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker,
                            Multiplicity.ONE, false, null, null),
                    null);

            Application app = applicationWithComponents(Map.of("en_only_humidity", enOnly));

            // Avec locale=fr , la colonne est masquée par langRestriction
            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(), app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Robustness {

        @Test
        @DisplayName("entries null -> set vide ( pas de NPE )")
        void nullEntriesSafe() {
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    null, app, REF_TYPE, LOCALE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("entries contient des nulls -> ignorés")
        void nullEntryInListSkipped() {
            java.util.List<FilterListEntry> entries = new java.util.ArrayList<>();
            entries.add(null);
            entries.add(new FilterList("ref", List.of(leafWithComponents(List.of("ok")))));
            entries.add(null);
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    entries, app, REF_TYPE, LOCALE);

            assertThat(result).containsExactly("ok");
        }

        @Test
        @DisplayName("application sans dataDescription pour le refType -> seules les sources /filters comptent")
        void missingDataDescriptionGracefulFallback() {
            Application app = mock(Application.class);
            when(app.findData(anyString())).thenReturn(Optional.empty());

            FilterList fl = new FilterList("ref", List.of(leafWithComponents(List.of("ok"))));
            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(fl), app, REF_TYPE, LOCALE);

            assertThat(result).containsExactly("ok");
        }

        @Test
        @DisplayName("résultat trié ( TreeSet ) pour ETag stable")
        void resultIsSorted() {
            FilterList fl = new FilterList("ref", List.of(
                    leafWithComponents(List.of("zebra", "alpha", "mango"))
            ));
            Application app = applicationWithComponents(Map.of());

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(fl), app, REF_TYPE, LOCALE);

            assertThat(List.copyOf(result)).containsExactly("alpha", "mango", "zebra");
        }
    }

    @Nested
    class Combined {

        @Test
        @DisplayName("mix FilterList + ColumnDistinctValues + interval config -> union dédoublonnée")
        void unionAcrossAllSources() {
            FilterList fl = new FilterList("site_acbb", List.of(
                    leafWithComponents(List.of("site"))
            ));
            ColumnDistinctValues cdv = new ColumnDistinctValues(
                    "method_id", List.of("A"), false, false);

            Application app = applicationWithComponents(Map.of(
                    "humidity", basicComponent("humidity",
                            new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker,
                                    Multiplicity.ONE, false, null, null)),
                    "label", basicComponent("label",
                            new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker,
                                    Multiplicity.ONE, false, ".*"))
            ));

            Set<String> result = FilterListVariablesExtractor.extract(
                    List.of(fl, cdv), app, REF_TYPE, LOCALE);

            assertThat(result).containsExactlyInAnyOrder("site", "method_id", "humidity");
        }
    }

    // ─── Helpers de construction des fixtures ────────────────────────────

    private static RefsLinked leafWithComponents(List<String> components) {
        return new RefsLinked(
                java.util.UUID.randomUUID(),
                Boolean.FALSE,
                "ref_type",
                null,
                null,
                null, null, null,
                List.of(),
                components);
    }

    private static BasicComponent basicComponent(String componentKey, CheckerDescription checker) {
        return new BasicComponent(
                ComponentDescription.ComponentDescriptionType.BasicComponent,
                componentKey,
                null,            // defaultValue
                Set.of(),        // tags ( pas de HiddenTag )
                null,            // importHeader
                null,            // exportHeaderName
                List.of(),       // langRestrictions ( pas de restriction )
                false,           // required
                null,            // mandatory
                checker,
                null);           // submissionAuthorizationScope
    }

    private static Application applicationWithComponents(Map<String, ComponentDescription> components) {
        Application app = mock(Application.class);
        StandardDataDescription dd = mock(StandardDataDescription.class);
        when(dd.componentDescriptions()).thenReturn(new LinkedHashMap<>(components));
        when(app.findData(anyString())).thenReturn(Optional.of(dd));
        return app;
    }
}
