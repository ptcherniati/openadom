package fr.inra.oresing.rest.model.synthesis;

import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour SynthesisResult (sans Spring / Docker).
 */
@Tag("domain.model")
@DisplayName("SynthesisResult – tests unitaires")
class SynthesisResultTest {

    private static OreSiSynthesis makeSynthesis(List<LocalDateTimeRange> ranges) {
        OreSiSynthesis s = new OreSiSynthesis();
        s.setApplication(UUID.randomUUID());
        s.setDatatype("myDataType");
        s.setVariable("myVariable");
        s.setRequiredAuthorizations(Map.of("scope", "FR"));
        s.setAggregation("sum");
        s.setRanges(ranges);
        return s;
    }

    @Test
    @DisplayName("constructeur copie les champs de OreSiSynthesis")
    void constructorCopiesFields() {
        OreSiSynthesis synthesis = makeSynthesis(List.of());
        SynthesisResult result = new SynthesisResult(synthesis);
        assertThat(result.getApplication()).isEqualTo(synthesis.getApplication());
        assertThat(result.getDatatype()).isEqualTo("myDataType");
        assertThat(result.getVariable()).isEqualTo("myVariable");
        assertThat(result.getRequiredAuthorizations()).containsEntry("scope", "FR");
        assertThat(result.getAggregation()).isEqualTo("sum");
    }

    @Test
    @DisplayName("constructeur avec ranges vides donne une liste de ranges vide")
    void constructorWithEmptyRanges() {
        OreSiSynthesis synthesis = makeSynthesis(List.of());
        SynthesisResult result = new SynthesisResult(synthesis);
        assertThat(result.getRanges()).isEmpty();
    }

    @Test
    @DisplayName("constructeur convertit les ranges en LocalDateTimeRangeResult")
    void constructorConvertsRanges() {
        LocalDateTimeRange range = LocalDateTimeRange.forYear(Year.of(2020));
        OreSiSynthesis synthesis = makeSynthesis(List.of(range));
        SynthesisResult result = new SynthesisResult(synthesis);
        assertThat(result.getRanges()).hasSize(1);
    }

    @Test
    @DisplayName("constructeur avec range illimité (always) produit des bornes vides")
    void constructorWithAlwaysRange() {
        LocalDateTimeRange always = LocalDateTimeRange.always();
        OreSiSynthesis synthesis = makeSynthesis(List.of(always));
        SynthesisResult result = new SynthesisResult(synthesis);
        assertThat(result.getRanges()).hasSize(1);
    }

    @Test
    @DisplayName("constructeur avec ranges null donne une liste vide")
    void constructorWithNullRanges() {
        OreSiSynthesis synthesis = makeSynthesis(null);
        SynthesisResult result = new SynthesisResult(synthesis);
        assertThat(result.getRanges()).isEmpty();
    }
}