package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.persistence.requestbuilder.data.DataRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour WithFormatForFilterDate via ComponentFiltersByDate.
 */
@Tag("domain.model")
@DisplayName("WithFormatForFilterDate – tests via ComponentFiltersByDate")
class WithFormatForFilterDateTest {

    @Test
    @DisplayName("getIsoStrings() retourne des chaînes ISO à partir de dates formatées")
    void getIsoStrings() {
        ComponentFiltersByDate f = new ComponentFiltersByDate(
                "obs",
                "dd/MM/yyyy",
                List.of("15/06/2020"),
                Multiplicity.ONE
        );
        List<String> isoStrings = f.getIsoStrings();
        assertThat(isoStrings).isNotNull();
        assertThat(isoStrings).hasSize(1);
        assertThat(isoStrings.get(0)).startsWith("2020-06-15");
    }

    @Test
    @DisplayName("getTimeStamps() retourne des Timestamps")
    void getTimeStamps() {
        ComponentFiltersByDate f = new ComponentFiltersByDate(
                "obs",
                "dd/MM/yyyy",
                List.of("01/01/2021"),
                Multiplicity.ONE
        );
        List<java.sql.Timestamp> ts = f.getTimeStamps();
        assertThat(ts).isNotNull().hasSize(1);
    }

    @Test
    @DisplayName("getIsoStrings() avec filtre epoch millis")
    void getIsoStringsWithEpochMillis() {
        // 2020-01-01 en millis UTC
        long millis = java.time.LocalDate.of(2020, 1, 1)
                .atStartOfDay(java.time.ZoneId.of("UTC"))
                .toInstant().toEpochMilli();
        ComponentFiltersByDate f = new ComponentFiltersByDate(
                "obs",
                "yyyy-MM-dd",
                List.of(String.valueOf(millis)),
                Multiplicity.ONE
        );
        List<String> isoStrings = f.getIsoStrings();
        assertThat(isoStrings).isNotNull();
        assertThat(isoStrings.get(0)).startsWith("2020-01-01");
    }

    @Test
    @DisplayName("filter() dans DataRequestBuilder avec ComponentFiltersByDate ONE")
    void filterWithComponentFiltersByDate() {
        ComponentFiltersByDate f = new ComponentFiltersByDate(
                "dateCol",
                "yyyy-MM-dd",
                List.of("2020-06-15"),
                Multiplicity.ONE
        );
        String result = DataRequestBuilder.filter(f);
        assertThat(result).contains("$.dateCol");
        assertThat(result).contains("date:");
    }

    @Test
    @DisplayName("filter() dans DataRequestBuilder avec ComponentFiltersByDate MANY")
    void filterWithComponentFiltersByDateMany() {
        ComponentFiltersByDate f = new ComponentFiltersByDate(
                "dateCol",
                "yyyy-MM-dd",
                List.of("2020-06-15"),
                Multiplicity.MANY
        );
        String result = DataRequestBuilder.filter(f);
        assertThat(result).contains("$[*].dateCol");
    }
}