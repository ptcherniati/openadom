package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour les records IntervalValues.
 * Ces tests ne nécessitent pas de contexte Spring.
 */
@DisplayName("Tests des IntervalValues")
@Tag("domain.model")
class IntervalValuesTest {

    // =========================================================================
    //  IntervalValuesDate
    // =========================================================================
    @Nested
    @DisplayName("IntervalValuesDate")
    class IntervalValuesDateTest {

        @Test
        @DisplayName("Création valide avec from et to textuels")
        void shouldCreateValidWithTextDates() {
            IntervalValuesDate iv = new IntervalValuesDate("2023-01-01", "2023-12-31", "yyyy-MM-dd");
            assertEquals("2023-01-01", iv.from());
            assertEquals("2023-12-31", iv.to());
            assertEquals("yyyy-MM-dd", iv.format());
        }

        @Test
        @DisplayName("Création valide avec from null (pas de borne inférieure)")
        void shouldCreateValidWithNullFrom() {
            IntervalValuesDate iv = new IntervalValuesDate(null, "2023-12-31", "yyyy-MM-dd");
            assertNull(iv.from());
            assertEquals("2023-12-31", iv.to());
        }

        @Test
        @DisplayName("Création valide avec to null (pas de borne supérieure)")
        void shouldCreateValidWithNullTo() {
            IntervalValuesDate iv = new IntervalValuesDate("2023-01-01", null, "yyyy-MM-dd");
            assertEquals("2023-01-01", iv.from());
            assertNull(iv.to());
        }

        @Test
        @DisplayName("Création valide avec from et to epoch millis")
        void shouldCreateValidWithEpochMillis() {
            // 2023-06-01 UTC
            String fromEpoch = "1685577600000";
            // 2023-12-31 UTC
            String toEpoch = "1703980800000";
            IntervalValuesDate iv = new IntervalValuesDate(fromEpoch, toEpoch, "yyyy-MM-dd");
            // Les valeurs doivent avoir été converties
            assertNotNull(iv.from());
            assertNotNull(iv.to());
            assertFalse(iv.from().matches("[0-9]+"));
            assertFalse(iv.to().matches("[0-9]+"));
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si format est null/vide")
        void shouldThrowWhenFormatNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDate("2023-01-01", "2023-12-31", null)
            );
            assertEquals(MISSING_FORMAT_FOR_INTERVAL_VALUE, ex.getMessage());

            assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDate("2023-01-01", "2023-12-31", "")
            );
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si from a un format invalide")
        void shouldThrowWhenFromBadFormat() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDate("not-a-date", "2023-12-31", "yyyy-MM-dd")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_START_DATE, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to a un format invalide")
        void shouldThrowWhenToBadFormat() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDate("2023-01-01", "not-a-date", "yyyy-MM-dd")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_END_DATE, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to est avant from")
        void shouldThrowWhenToBeforeFrom() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDate("2023-12-31", "2023-01-01", "yyyy-MM-dd")
            );
            assertEquals(FILTER_BAD_FORMAT_BAD_RANGE_FOR_DATES, ex.getMessage());
            assertNotNull(ex.getParams());
            assertEquals("2023-12-31", ex.getParams().get("from"));
            assertEquals("2023-01-01", ex.getParams().get("to"));
        }

        @Test
        @DisplayName("Création valide avec from égal à to (même date)")
        void shouldCreateValidWhenFromEqualsTo() {
            // from == to doit être valide
            IntervalValuesDate iv = new IntervalValuesDate("2023-06-15", "2023-06-15", "yyyy-MM-dd");
            assertEquals("2023-06-15", iv.from());
            assertEquals("2023-06-15", iv.to());
        }
    }

    // =========================================================================
    //  IntervalValuesNumeric
    // =========================================================================
    @Nested
    @DisplayName("IntervalValuesNumeric")
    class IntervalValuesNumericTest {

        @Test
        @DisplayName("Création valide avec from et to")
        void shouldCreateValid() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric("1.5", "10.0");
            assertEquals("1.5", iv.from());
            assertEquals("10.0", iv.to());
        }

        @Test
        @DisplayName("Création valide avec from null")
        void shouldCreateValidWithNullFrom() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric(null, "10.0");
            assertNull(iv.from());
        }

        @Test
        @DisplayName("Création valide avec to null")
        void shouldCreateValidWithNullTo() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric("1.5", null);
            assertNull(iv.to());
        }

        @Test
        @DisplayName("Création valide avec from = to")
        void shouldCreateValidWhenFromEqualsTo() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric("5.0", "5.0");
            assertEquals("5.0", iv.from());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si from n'est pas numérique")
        void shouldThrowWhenFromNotNumeric() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesNumeric("abc", "10.0")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_START_NUMERIC, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to n'est pas numérique")
        void shouldThrowWhenToNotNumeric() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesNumeric("1.5", "xyz")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_END_NUMERIC, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to est inférieur à from")
        void shouldThrowWhenToLessThanFrom() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesNumeric("10.0", "1.5")
            );
            assertEquals(FILTER_BAD_FORMAT_BAD_RANGE_FOR_NUMERICS, ex.getMessage());
            assertNotNull(ex.getParams());
        }

        @Test
        @DisplayName("fromFromNumeric() retourne Integer.MIN_VALUE si from est null")
        void fromFromNumericReturnsMinValueWhenFromNull() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric(null, "10.0");
            assertEquals(Integer.MIN_VALUE, iv.fromFromNumeric());
        }

        @Test
        @DisplayName("fromToNumeric() retourne Integer.MAX_VALUE si to est null")
        void fromToNumericReturnsMaxValueWhenToNull() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric("1.5", null);
            assertEquals(Integer.MAX_VALUE, iv.fromToNumeric());
        }

        @Test
        @DisplayName("fromFromNumeric() retourne la valeur parsée")
        void fromFromNumericReturnsParsedValue() {
            IntervalValuesNumeric iv = new IntervalValuesNumeric("3.14", "10.0");
            assertEquals(3.14, iv.fromFromNumeric().doubleValue(), 0.001);
        }
    }

    // =========================================================================
    //  IntervalValuesTime
    // =========================================================================
    @Nested
    @DisplayName("IntervalValuesTime")
    class IntervalValuesTimeTest {

        @Test
        @DisplayName("Création valide avec from et to")
        void shouldCreateValid() {
            IntervalValuesTime iv = new IntervalValuesTime("08:00", "18:00", "HH:mm");
            assertEquals("08:00", iv.from());
            assertEquals("18:00", iv.to());
            assertEquals("HH:mm", iv.format());
        }

        @Test
        @DisplayName("Création valide avec from null")
        void shouldCreateValidWithNullFrom() {
            IntervalValuesTime iv = new IntervalValuesTime(null, "18:00", "HH:mm");
            assertNull(iv.from());
        }

        @Test
        @DisplayName("Création valide avec to null")
        void shouldCreateValidWithNullTo() {
            IntervalValuesTime iv = new IntervalValuesTime("08:00", null, "HH:mm");
            assertNull(iv.to());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si format est null/vide")
        void shouldThrowWhenFormatNull() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesTime("08:00", "18:00", null)
            );
            assertEquals(MISSING_FORMAT_FOR_INTERVAL_VALUE, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si from a un format invalide")
        void shouldThrowWhenFromBadFormat() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesTime("not-a-time", "18:00", "HH:mm")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_START_TIME, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to a un format invalide")
        void shouldThrowWhenToBadFormat() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesTime("08:00", "not-a-time", "HH:mm")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_END_TIME, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to est avant from")
        void shouldThrowWhenToBeforeFrom() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesTime("18:00", "08:00", "HH:mm")
            );
            assertEquals(FILTER_BAD_FORMAT_BAD_RANGE_FOR_TIMES, ex.getMessage());
        }
    }

    // =========================================================================
    //  IntervalValuesDateTime
    // =========================================================================

    @Nested
    @DisplayName("IntervalValuesDateTime")
    class IntervalValuesDateTimeTest {

        @Test
        @DisplayName("Création valide avec from et to en datetime")
        void shouldCreateValidWithDateTimes() {
            IntervalValuesDateTime iv = new IntervalValuesDateTime(
                    "2023-01-01T08:00", "2023-12-31T18:00", "yyyy-MM-dd'T'HH:mm");
            assertThat(iv.from()).isEqualTo("2023-01-01T08:00");
            assertThat(iv.to()).isEqualTo("2023-12-31T18:00");
            assertThat(iv.format()).isEqualTo("yyyy-MM-dd'T'HH:mm");
        }

        @Test
        @DisplayName("Création valide avec from et to null")
        void shouldCreateValidWithNullBounds() {
            assertDoesNotThrow(() -> new IntervalValuesDateTime(null, null, "yyyy-MM-dd'T'HH:mm"));
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si format null ou vide")
        void shouldThrowWhenFormatMissing() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDateTime("2023-01-01T08:00", "2023-01-02T18:00", null)
            );
            assertEquals(MISSING_FORMAT_FOR_INTERVAL_VALUE, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si from a un format invalide")
        void shouldThrowWhenFromBadFormat() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDateTime("not-a-datetime", "2023-12-31T18:00", "yyyy-MM-dd'T'HH:mm")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_START_DATE_TIME, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to a un format invalide")
        void shouldThrowWhenToBadFormat() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDateTime("2023-01-01T08:00", "not-a-datetime", "yyyy-MM-dd'T'HH:mm")
            );
            assertEquals(FILTER_BAD_FORMAT_FOR_END_DATE_TIME, ex.getMessage());
        }

        @Test
        @DisplayName("Lance BadDownloadDatasetQuery si to est avant from")
        void shouldThrowWhenToBeforeFrom() {
            BadDownloadDatasetQuery ex = assertThrows(BadDownloadDatasetQuery.class, () ->
                    new IntervalValuesDateTime("2023-12-31T18:00", "2023-01-01T08:00", "yyyy-MM-dd'T'HH:mm")
            );
            assertEquals(FILTER_BAD_FORMAT_BAD_RANGE_FOR_DATE_TIMES, ex.getMessage());
        }
    }
}