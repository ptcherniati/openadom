package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour IntervalValuesDate, IntervalValuesDateTime
 * et les méthodes default de WithFormatForIntervalDate (sans Spring / sans Docker).
 */
@Tag("domain.model")
@DisplayName("WithFormatForIntervalDate – tests unitaires")
class WithFormatForIntervalDateTest {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // =========================================================================
    //  IntervalValuesDate
    // =========================================================================

    @Nested
    @DisplayName("IntervalValuesDate")
    class IntervalValuesDateTest {

        @Test
        @DisplayName("crée un intervalle valide avec from et to")
        void createsValidIntervalWithFromAndTo() {
            IntervalValuesDate interval = new IntervalValuesDate("2020-01-01", "2020-12-31", DATE_FORMAT);
            assertThat(interval.from()).isEqualTo("2020-01-01");
            assertThat(interval.to()).isEqualTo("2020-12-31");
        }

        @Test
        @DisplayName("crée un intervalle sans from ni to")
        void createsIntervalWithNullBounds() {
            IntervalValuesDate interval = new IntervalValuesDate(null, null, DATE_FORMAT);
            assertThat(interval.from()).isNull();
            assertThat(interval.to()).isNull();
        }

        @Test
        @DisplayName("accepte from epoch millis et le convertit en chaîne formatée")
        void acceptsEpochMillisForFrom() {
            // 2020-01-01 en millis UTC
            long millis = java.time.LocalDate.of(2020, 1, 1)
                    .atStartOfDay(java.time.ZoneId.of("UTC"))
                    .toInstant().toEpochMilli();
            IntervalValuesDate interval = new IntervalValuesDate(String.valueOf(millis), null, DATE_FORMAT);
            assertThat(interval.from()).isEqualTo("2020-01-01");
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si le format est manquant")
        void throwsWhenFormatMissing() {
            assertThatThrownBy(() -> new IntervalValuesDate("2020-01-01", null, ""))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si le format est null")
        void throwsWhenFormatNull() {
            assertThatThrownBy(() -> new IntervalValuesDate("2020-01-01", null, null))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si from est au mauvais format")
        void throwsWhenFromFormatInvalid() {
            assertThatThrownBy(() -> new IntervalValuesDate("not-a-date", null, DATE_FORMAT))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si to est au mauvais format")
        void throwsWhenToFormatInvalid() {
            assertThatThrownBy(() -> new IntervalValuesDate(null, "invalid", DATE_FORMAT))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si to est avant from")
        void throwsWhenToBeforeFrom() {
            assertThatThrownBy(() -> new IntervalValuesDate("2021-01-01", "2020-01-01", DATE_FORMAT))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }
    }

    // =========================================================================
    //  IntervalValuesDateTime
    // =========================================================================

    @Nested
    @DisplayName("IntervalValuesDateTime")
    class IntervalValuesDateTimeTest {

        @Test
        @DisplayName("crée un intervalle valide avec from et to")
        void createsValidIntervalWithFromAndTo() {
            IntervalValuesDateTime interval = new IntervalValuesDateTime(
                    "2020-01-01 08:00:00", "2020-12-31 23:59:59", DATETIME_FORMAT);
            assertThat(interval.from()).isEqualTo("2020-01-01 08:00:00");
            assertThat(interval.to()).isEqualTo("2020-12-31 23:59:59");
        }

        @Test
        @DisplayName("crée un intervalle avec seulement from")
        void createsIntervalWithFromOnly() {
            IntervalValuesDateTime interval = new IntervalValuesDateTime(
                    "2020-01-01 00:00:00", null, DATETIME_FORMAT);
            assertThat(interval.from()).isNotNull();
            assertThat(interval.to()).isNull();
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si le format est vide")
        void throwsWhenFormatEmpty() {
            assertThatThrownBy(() -> new IntervalValuesDateTime("2020-01-01 00:00:00", null, ""))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si from a un mauvais format")
        void throwsWhenFromInvalid() {
            assertThatThrownBy(() -> new IntervalValuesDateTime("not-datetime", null, DATETIME_FORMAT))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }

        @Test
        @DisplayName("lève BadDownloadDatasetQuery si to est avant from")
        void throwsWhenToBeforeFrom() {
            assertThatThrownBy(() -> new IntervalValuesDateTime(
                    "2021-06-01 00:00:00", "2020-01-01 00:00:00", DATETIME_FORMAT))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }
    }

    // =========================================================================
    //  WithFormatForIntervalDate – méthodes default (via IntervalValuesDate)
    // =========================================================================

    @Nested
    @DisplayName("WithFormatForIntervalDate – méthodes default")
    class DefaultMethodsTest {

        @Test
        @DisplayName("fromTimestamp() retourne le Timestamp correspondant à from")
        void fromTimestampReturnsCorrectTimestamp() {
            IntervalValuesDate interval = new IntervalValuesDate("2020-06-15", null, DATE_FORMAT);
            Timestamp ts = interval.fromTimestamp();
            assertThat(ts).isNotNull();
        }

        @Test
        @DisplayName("fromTimestamp() retourne Timestamp MIN quand from est null")
        void fromTimestampReturnsMinWhenNull() {
            IntervalValuesDate interval = new IntervalValuesDate(null, null, DATE_FORMAT);
            Timestamp ts = interval.fromTimestamp();
            assertThat(ts.getTime()).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("toTimestamp() retourne Timestamp MAX quand to est null")
        void toTimestampReturnsMaxWhenNull() {
            IntervalValuesDate interval = new IntervalValuesDate(null, null, DATE_FORMAT);
            Timestamp ts = interval.toTimestamp();
            assertThat(ts.getTime()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("getFromIsoString() retourne une chaîne ISO valide")
        void getFromIsoStringReturnsIsoString() {
            IntervalValuesDate interval = new IntervalValuesDate("2020-06-15", "2020-12-31", DATE_FORMAT);
            String iso = interval.getFromIsoString();
            assertThat(iso).startsWith("2020-06-15");
        }

        @Test
        @DisplayName("getToIsoString() retourne une chaîne ISO valide (décalée d'un jour)")
        void getToIsoStringReturnsNextDay() {
            IntervalValuesDate interval = new IntervalValuesDate("2020-01-01", "2020-06-15", DATE_FORMAT);
            String iso = interval.getToIsoString();
            // toTimestamp + 1 jour = 2020-06-16
            assertThat(iso).startsWith("2020-06-16");
        }

        @Test
        @DisplayName("getFromIsoString() retourne MIN ISO quand from est null")
        void getFromIsoStringReturnsFallbackWhenNull() {
            IntervalValuesDate interval = new IntervalValuesDate(null, null, DATE_FORMAT);
            String iso = interval.getFromIsoString();
            assertThat(iso).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("getToIsoString() retourne une valeur quand to est null")
        void getToIsoStringReturnsFallbackWhenNull() {
            IntervalValuesDate interval = new IntervalValuesDate(null, null, DATE_FORMAT);
            String iso = interval.getToIsoString();
            assertThat(iso).isNotNull().isNotEmpty();
        }
    }
}