package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;
import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_INTERVAL_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour les records de filtres de requêtes de données.
 * Ces tests ne nécessitent pas de contexte Spring.
 */
@DisplayName("Tests des ComponentFilters")
@Tag("domain.model")
class ComponentFiltersTest {

    // =========================================================================
    //  ComponentFiltersByDate
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersByDate")
    class ComponentFiltersByDateTest {

        @Test
        @DisplayName("Création valide avec filtre texte au format date")
        void shouldCreateValidWithTextFilter() {
            ComponentFiltersByDate filter = new ComponentFiltersByDate(
                    "myDate", "dd/MM/yyyy", List.of("15/06/2023"), Multiplicity.ONE
            );
            assertEquals("myDate", filter.componentKey());
            assertEquals("dd/MM/yyyy", filter.format());
            assertEquals(List.of("15/06/2023"), filter.filters());
        }

        @Test
        @DisplayName("Création valide avec epoch millis converti en date texte")
        void shouldCreateValidWithEpochMillis() {
            String epochMs = "1686787200000"; // 2023-06-15 UTC
            ComponentFiltersByDate filter = new ComponentFiltersByDate(
                    "myDate", "yyyy-MM-dd", List.of(epochMs), Multiplicity.ONE
            );
            // La valeur doit avoir été convertie
            String expectedDate = LocalDate.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(epochMs)), ZoneId.of("UTC")
            ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            assertEquals(List.of(expectedDate), filter.filters());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByDate(null, "dd/MM/yyyy", List.of("15/06/2023"), Multiplicity.ONE)
            );
            assertEquals(MISSING_COMPONENT_KEY_FOR_SEARCH, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si format est null/vide")
        void shouldThrowWhenFormatEmpty() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByDate("key", null, List.of("15/06/2023"), Multiplicity.ONE)
            );
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByDate("key", "", List.of("15/06/2023"), Multiplicity.ONE)
            );
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si filters est vide ou contient null/vide")
        void shouldThrowWhenFiltersEmpty() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByDate("key", "dd/MM/yyyy", List.of(), Multiplicity.ONE)
            );
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByDate("key", "dd/MM/yyyy", List.of(""), Multiplicity.ONE)
            );
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByDate("key", "dd/MM/yyyy", null, Multiplicity.ONE)
            );
        }
    }

    // =========================================================================
    //  ComponentFiltersByNumeric
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersByNumeric")
    class ComponentFiltersByNumericTest {

        @Test
        @DisplayName("Création valide")
        void shouldCreateValid() {
            ComponentFiltersByNumeric filter = new ComponentFiltersByNumeric(
                    "temperature", List.of("20.5", "30.1"), Multiplicity.MANY
            );
            assertEquals("temperature", filter.componentKey());
            assertEquals(List.of("20.5", "30.1"), filter.filters());
            assertEquals(Multiplicity.MANY, filter.multiplicity());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByNumeric(null, List.of("5.0"), Multiplicity.ONE)
            );
            assertEquals(MISSING_COMPONENT_KEY_FOR_SEARCH, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si filters est vide ou contient vide")
        void shouldThrowWhenFiltersEmpty() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByNumeric("temp", List.of(), Multiplicity.ONE)
            );
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByNumeric("temp", List.of(""), Multiplicity.ONE)
            );
        }
    }

    // =========================================================================
    //  ComponentFiltersByReference
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersByReference")
    class ComponentFiltersByReferenceTest {

        @Test
        @DisplayName("Création valide")
        void shouldCreateValid() {
            ComponentFiltersByReference filter = new ComponentFiltersByReference(
                    "site", List.of("site1", "site2"), Multiplicity.MANY
            );
            assertEquals("site", filter.componentKey());
            assertEquals(List.of("site1", "site2"), filter.filters());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByReference(null, List.of("ref"), Multiplicity.ONE)
            );
            assertEquals(MISSING_COMPONENT_KEY_FOR_SEARCH, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si filters contient une chaîne vide")
        void shouldThrowWhenFiltersContainEmpty() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByReference("site", List.of("valid", ""), Multiplicity.ONE)
            );
        }
    }

    // =========================================================================
    //  ComponentFiltersByBoolean
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersByBoolean")
    class ComponentFiltersByBooleanTest {

        @Test
        @DisplayName("Création valide")
        void shouldCreateValid() {
            ComponentFiltersByBoolean filter = new ComponentFiltersByBoolean(
                    "actif", List.of("true"), Multiplicity.ONE
            );
            assertEquals("actif", filter.componentKey());
            assertEquals(List.of("true"), filter.filters());
        }

        @Test
        @DisplayName("Création valide sans filtre (pas de validation sur filters)")
        void shouldCreateValidWithNullFilters() {
            // ComponentFiltersByBoolean ne valide pas les filters (contrairement aux autres)
            ComponentFiltersByBoolean filter = new ComponentFiltersByBoolean(
                    "actif", null, Multiplicity.ONE
            );
            assertNotNull(filter);
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersByBoolean(null, List.of("true"), Multiplicity.ONE)
            );
            assertEquals(MISSING_COMPONENT_KEY_FOR_SEARCH, ex.getMessage());
        }
    }

    // =========================================================================
    //  ComponentFiltersForWordByPlainText
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersForWordByPlainText")
    class ComponentFiltersForWordByPlainTextTest {

        @Test
        @DisplayName("Création valide")
        void shouldCreateValid() {
            ComponentFiltersForWordByPlainText filter = new ComponentFiltersForWordByPlainText(
                    "description", List.of("mot1", "mot2"), Multiplicity.ONE
            );
            assertEquals("description", filter.componentKey());
            assertEquals(List.of("mot1", "mot2"), filter.filters());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForWordByPlainText(null, List.of("mot"), Multiplicity.ONE)
            );
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si filters est vide")
        void shouldThrowWhenFiltersEmpty() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForWordByPlainText("desc", List.of(), Multiplicity.ONE)
            );
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si filters contient une chaîne vide")
        void shouldThrowWhenFiltersContainEmpty() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForWordByPlainText("desc", List.of("mot", ""), Multiplicity.ONE)
            );
        }
    }

    // =========================================================================
    //  ComponentFiltersForWordByRegexp
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersForWordByRegexp")
    class ComponentFiltersForWordByRegexpTest {

        @Test
        @DisplayName("Création valide avec pattern regex")
        void shouldCreateValid() {
            ComponentFiltersForWordByRegexp filter = new ComponentFiltersForWordByRegexp(
                    "description", List.of(".*test.*"), Multiplicity.ONE
            );
            assertEquals("description", filter.componentKey());
            assertEquals(List.of(".*test.*"), filter.filters());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForWordByRegexp(null, List.of("pattern"), Multiplicity.ONE)
            );
        }
    }

    // =========================================================================
    //  ComponentFiltersForIntervalByDate
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersForIntervalByDate")
    class ComponentFiltersForIntervalByDateTest {

        @Test
        @DisplayName("Création valide avec intervalles")
        void shouldCreateValid() {
            IntervalValuesDate interval = new IntervalValuesDate("2023-01-01", "2023-12-31", "yyyy-MM-dd");
            ComponentFiltersForIntervalByDate filter = new ComponentFiltersForIntervalByDate(
                    "periode", List.of(interval), Multiplicity.ONE
            );
            assertEquals("periode", filter.componentKey());
            assertEquals(1, filter.intervalsValues().size());
        }

        @Test
        @DisplayName("Création valide avec intervalles null")
        void shouldCreateValidWithNullIntervals() {
            // La validation se fait dans IntervalValuesDate, pas ici
            ComponentFiltersForIntervalByDate filter = new ComponentFiltersForIntervalByDate(
                    "periode", null, Multiplicity.ONE
            );
            assertNotNull(filter);
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForIntervalByDate(null, List.of(), Multiplicity.ONE)
            );
            assertEquals(MISSING_COMPONENT_KEY_FOR_SEARCH, ex.getMessage());
        }
    }

    // =========================================================================
    //  ComponentFiltersForIntervalByNumeric
    // =========================================================================
    @Nested
    @DisplayName("ComponentFiltersForIntervalByNumeric")
    class ComponentFiltersForIntervalByNumericTest {

        @Test
        @DisplayName("Création valide")
        void shouldCreateValid() {
            IntervalValuesNumeric interval = new IntervalValuesNumeric("1.5", "10.0");
            ComponentFiltersForIntervalByNumeric filter = new ComponentFiltersForIntervalByNumeric(
                    "valeur", List.of(interval), Multiplicity.ONE
            );
            assertEquals("valeur", filter.componentKey());
            assertEquals(1, filter.intervalsValues().size());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si componentKey est null")
        void shouldThrowWhenComponentKeyNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForIntervalByNumeric(null, List.of(), Multiplicity.ONE)
            );
            assertEquals(MISSING_COMPONENT_KEY_FOR_SEARCH, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si intervalsValues est null")
        void shouldThrowWhenIntervalsValuesNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new ComponentFiltersForIntervalByNumeric("valeur", null, Multiplicity.ONE)
            );
            assertEquals(MISSING_INTERVAL_VALUE, ex.getMessage());
        }
    }
}