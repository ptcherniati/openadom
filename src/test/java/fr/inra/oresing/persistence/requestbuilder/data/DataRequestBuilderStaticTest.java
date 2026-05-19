package fr.inra.oresing.persistence.requestbuilder.data;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.BasicComponent;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les méthodes statiques de DataRequestBuilder (sans Spring / Docker).
 */
@Tag("core.config")
@DisplayName("DataRequestBuilder – méthodes utilitaires statiques")
class DataRequestBuilderStaticTest {

    // =========================================================================
    //  sanitize
    // =========================================================================

    @Nested
    @DisplayName("sanitize")
    class SanitizeTest {

        @Test
        @DisplayName("remplace les apostrophes par doubles apostrophes")
        void sanitizeSingleQuote() {
            String result = DataRequestBuilder.sanitize("it's");
            assertThat(result).isEqualTo("it''s");
        }

        @Test
        @DisplayName("remplace :: par .")
        void sanitizeDoubleColon() {
            String result = DataRequestBuilder.sanitize("col::text");
            assertThat(result).isEqualTo("col.text");
        }

        @Test
        @DisplayName("retourne null pour null")
        void sanitizeNull() {
            assertThat(DataRequestBuilder.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("laisse une chaîne normale inchangée")
        void sanitizeNormal() {
            assertThat(DataRequestBuilder.sanitize("hello")).isEqualTo("hello");
        }
    }

    // =========================================================================
    //  sanitizeJsonPathStringValue
    // =========================================================================

    @Nested
    @DisplayName("sanitizeJsonPathStringValue")
    class SanitizeJsonPathTest {

        @Test
        @DisplayName("échappe les antislashs")
        void escapesBackslash() {
            assertThat(DataRequestBuilder.sanitizeJsonPathStringValue("a\\b")).isEqualTo("a\\\\b");
        }

        @Test
        @DisplayName("échappe les guillemets doubles")
        void escapesDoubleQuote() {
            assertThat(DataRequestBuilder.sanitizeJsonPathStringValue("say \"hello\"")).isEqualTo("say \\\"hello\\\"");
        }

        @Test
        @DisplayName("échappe les apostrophes")
        void escapesQuote() {
            assertThat(DataRequestBuilder.sanitizeJsonPathStringValue("it's")).isEqualTo("it''s");
        }

        @Test
        @DisplayName("double les pourcentages")
        void escapesPercent() {
            assertThat(DataRequestBuilder.sanitizeJsonPathStringValue("50%")).isEqualTo("50%%");
        }

        @Test
        @DisplayName("retourne null pour null")
        void nullInput() {
            assertThat(DataRequestBuilder.sanitizeJsonPathStringValue(null)).isNull();
        }
    }

    // =========================================================================
    //  buildEqualityPredicate
    // =========================================================================

    @Nested
    @DisplayName("buildEqualityPredicate")
    class EqualityPredicateTest {

        @Test
        @DisplayName("null → @ == null")
        void nullFilter() {
            assertThat(DataRequestBuilder.buildEqualityPredicate(null)).isEqualTo("@ == null");
        }

        @Test
        @DisplayName("valeur normale → @ == \"val\"")
        void normalFilter() {
            assertThat(DataRequestBuilder.buildEqualityPredicate("hello")).isEqualTo("@ == \"hello\"");
        }

        @Test
        @DisplayName("valeur avec guillemets → échappée")
        void filterWithQuotes() {
            String pred = DataRequestBuilder.buildEqualityPredicate("a\"b");
            assertThat(pred).contains("\\\"");
        }
    }

    // =========================================================================
    //  buildReferencePredicate
    // =========================================================================

    @Nested
    @DisplayName("buildReferencePredicate")
    class ReferencePredicateTest {

        @Test
        @DisplayName("null → @ == null")
        void nullFilter() {
            assertThat(DataRequestBuilder.buildReferencePredicate(null)).isEqualTo("@ == null");
        }

        @Test
        @DisplayName("valeur normale contient equality et starts with")
        void normalFilter() {
            String pred = DataRequestBuilder.buildReferencePredicate("parent");
            assertThat(pred).contains("@ == \"parent\"");
            assertThat(pred).contains("starts with");
            assertThat(pred).contains("parent.");
        }
    }

    // =========================================================================
    //  buildRegexpPredicate
    // =========================================================================

    @Nested
    @DisplayName("buildRegexpPredicate")
    class RegexpPredicateTest {

        @Test
        @DisplayName("null → @ == null")
        void nullFilter() {
            assertThat(DataRequestBuilder.buildRegexpPredicate(null)).isEqualTo("@ == null");
        }

        @Test
        @DisplayName("valeur normale contient like_regex")
        void normalFilter() {
            String pred = DataRequestBuilder.buildRegexpPredicate("abc");
            assertThat(pred).contains("like_regex");
            assertThat(pred).contains("abc");
        }
    }

    // =========================================================================
    //  filter(ComponentFilters)
    // =========================================================================

    @Nested
    @DisplayName("filter(ComponentFilters)")
    class FilterTest {

        @Test
        @DisplayName("NoComponentFilters → null")
        void noComponentFilters() {
            assertThat(DataRequestBuilder.filter(new NoComponentFilters())).isNull();
        }

        @Test
        @DisplayName("ComponentFiltersByBoolean ONE → JSONPath $.compKey")
        void booleanFilterOne() {
            ComponentFiltersByBoolean f = new ComponentFiltersByBoolean(
                    "myBool", List.of("true"), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.myBool");
        }

        @Test
        @DisplayName("ComponentFiltersByBoolean MANY → JSONPath $[*].compKey")
        void booleanFilterMany() {
            ComponentFiltersByBoolean f = new ComponentFiltersByBoolean(
                    "myBool", List.of("false"), Multiplicity.MANY);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].myBool");
        }

        @Test
        @DisplayName("ComponentFiltersByNumeric ONE → JSONPath $.compKey")
        void numericFilterOne() {
            ComponentFiltersByNumeric f = new ComponentFiltersByNumeric(
                    "temp", List.of("42"), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.temp");
            assertThat(result).contains("double()");
        }

        @Test
        @DisplayName("ComponentFiltersByNumeric MANY → JSONPath $[*].compKey")
        void numericFilterMany() {
            ComponentFiltersByNumeric f = new ComponentFiltersByNumeric(
                    "temp", List.of("10"), Multiplicity.MANY);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].temp");
        }

        @Test
        @DisplayName("ComponentFiltersByReference ONE → JSONPath $.compKey avec starts with")
        void referenceFilterOne() {
            ComponentFiltersByReference f = new ComponentFiltersByReference(
                    "site", List.of("FR.Paris"), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.site");
            assertThat(result).contains("starts with");
        }

        @Test
        @DisplayName("ComponentFiltersByReference MANY → JSONPath $[*].compKey")
        void referenceFilterMany() {
            ComponentFiltersByReference f = new ComponentFiltersByReference(
                    "site", List.of("FR.Paris"), Multiplicity.MANY);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].site");
        }

        @Test
        @DisplayName("ComponentFiltersForWordByPlainText ONE → JSONPath $.compKey")
        void plainTextFilterOne() {
            ComponentFiltersForWordByPlainText f = new ComponentFiltersForWordByPlainText(
                    "label", List.of("hello"), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.label");
        }

        @Test
        @DisplayName("ComponentFiltersForWordByPlainText MANY → JSONPath $[*].compKey")
        void plainTextFilterMany() {
            ComponentFiltersForWordByPlainText f = new ComponentFiltersForWordByPlainText(
                    "label", List.of("world"), Multiplicity.MANY);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].label");
        }

        @Test
        @DisplayName("ComponentFiltersForWordByRegexp ONE → JSONPath $.compKey avec like_regex")
        void regexpFilterOne() {
            ComponentFiltersForWordByRegexp f = new ComponentFiltersForWordByRegexp(
                    "desc", List.of("foo.*"), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.desc");
            assertThat(result).contains("like_regex");
        }

        @Test
        @DisplayName("ComponentFiltersForWordByRegexp MANY → JSONPath $[*].compKey")
        void regexpFilterMany() {
            ComponentFiltersForWordByRegexp f = new ComponentFiltersForWordByRegexp(
                    "desc", List.of("bar"), Multiplicity.MANY);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].desc");
        }

        @Test
        @DisplayName("ComponentFiltersForIntervalByNumeric ONE → JSONPath $.compKey avec >=")
        void intervalNumericFilterOne() {
            ComponentFiltersForIntervalByNumeric f = new ComponentFiltersForIntervalByNumeric(
                    "measure",
                    List.of(new IntervalValuesNumeric("10.0", "20.0")),
                    Multiplicity.ONE
            );
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.measure");
            assertThat(result).contains(">=");
        }

        @Test
        @DisplayName("ComponentFiltersForIntervalByNumeric MANY → JSONPath $[*].compKey")
        void intervalNumericFilterMany() {
            ComponentFiltersForIntervalByNumeric f = new ComponentFiltersForIntervalByNumeric(
                    "measure",
                    List.of(new IntervalValuesNumeric("5.0", "15.0")),
                    Multiplicity.MANY
            );
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].measure");
        }

        @Test
        @DisplayName("ComponentFiltersForIntervalByDate ONE → JSONPath $.compKey avec date:")
        void intervalDateFilterOne() {
            IntervalValuesDate interval = new IntervalValuesDate("2020-01-01", "2020-12-31", "yyyy-MM-dd");
            ComponentFiltersForIntervalByDate f = new ComponentFiltersForIntervalByDate(
                    "obs_date",
                    List.of(interval),
                    Multiplicity.ONE
            );
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$.obs_date");
            assertThat(result).contains("date:");
        }

        @Test
        @DisplayName("ComponentFiltersForIntervalByDate MANY → JSONPath $[*].compKey")
        void intervalDateFilterMany() {
            IntervalValuesDate interval = new IntervalValuesDate("2021-01-01", "2021-12-31", "yyyy-MM-dd");
            ComponentFiltersForIntervalByDate f = new ComponentFiltersForIntervalByDate(
                    "obs_date",
                    List.of(interval),
                    Multiplicity.MANY
            );
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("$[*].obs_date");
        }

        @Test
        @DisplayName("ComponentFiltersByReference avec null → @ == null")
        void referenceFilterWithNull() {
            ComponentFiltersByReference f = new ComponentFiltersByReference(
                    "site", Arrays.asList((String) null), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("@ == null");
        }

        @Test
        @DisplayName("ComponentFiltersForWordByPlainText avec null → @ == null")
        void plainTextFilterWithNull() {
            ComponentFiltersForWordByPlainText f = new ComponentFiltersForWordByPlainText(
                    "label", Arrays.asList((String) null), Multiplicity.ONE);
            String result = DataRequestBuilder.filter(f);
            assertThat(result).contains("@ == null");
        }
    }

    // =========================================================================
    //  SelectedComponent.of()
    // =========================================================================

    @Nested
    @DisplayName("SelectedComponent.of")
    class SelectedComponentOfTest {

        private BasicComponent basicComponent(String key, CheckerDescription checker) {
            return new BasicComponent(
                    ComponentDescription.ComponentDescriptionType.BasicComponent,
                    key,
                    null,
                    Set.of(),
                    key,
                    key,
                    List.of(),
                    false,
                    ComponentPresenceConstraint.OPTIONAL,
                    checker,
                    null
            );
        }

        @Test
        @DisplayName("composant non-référence → refsLinkedToPathToHide null")
        void nonReferenceComponent() {
            BasicComponent comp = basicComponent("label", CheckerDescription.NO_CHECKER);
            List<BuildRemoveSqlSelectNotInValues> result =
                    SelectedComponent.of(List.of(Map.entry("label", comp)));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).valuePathToHide()).contains("label");
            assertThat(result.get(0).refsLinkedToPathToHide()).isNull();
        }

        @Test
        @DisplayName("composant référence → refsLinkedToPathToHide non null")
        void referenceComponent() {
            ReferenceChecker refChecker = new ReferenceChecker(
                    CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                    "site",
                    Multiplicity.ONE,
                    false,
                    "typeSite",
                    false,
                    false
            );
            BasicComponent comp = basicComponent("site", refChecker);
            List<BuildRemoveSqlSelectNotInValues> result =
                    SelectedComponent.of(List.of(Map.entry("site", comp)));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).valuePathToHide()).contains("site");
            assertThat(result.get(0).refsLinkedToPathToHide()).contains("site");
        }

        @Test
        @DisplayName("liste vide → résultat vide")
        void emptyList() {
            List<BuildRemoveSqlSelectNotInValues> result = SelectedComponent.of(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("plusieurs composants → autant d'entrées")
        void multipleComponents() {
            BasicComponent c1 = basicComponent("col1", CheckerDescription.NO_CHECKER);
            BasicComponent c2 = basicComponent("col2", CheckerDescription.NO_CHECKER);
            List<Map.Entry<String, ComponentDescription>> entries = List.of(
                    Map.entry("col1", c1),
                    Map.entry("col2", c2)
            );
            List<BuildRemoveSqlSelectNotInValues> result = SelectedComponent.of(entries);
            assertThat(result).hasSize(2);
        }
    }
}