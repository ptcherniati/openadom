package fr.inra.oresing.rest.data.extraction;

import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.read.query.ComponentOrderBy;
import fr.inra.oresing.domain.data.read.query.ComponentTextType;
import fr.inra.oresing.domain.repository.data.DataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs de {@link DataCsvRowBuilder} — aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("DataCsvRowBuilder — getCsvRow()")
class DataCsvRowBuilderTest {

    @Test
    @DisplayName("getCsvRow() retourne les valeurs des colonnes dans l'ordre")
    void getCsvRowReturnsColumnValuesInOrder() {
        DataCsvRowBuilder builder = new DataCsvRowBuilder("fr", null, null, false);
        ComponentOrderBy colA = new ComponentOrderBy("a", DataRepository.Order.ASC, new ComponentTextType());
        ComponentOrderBy colB = new ComponentOrderBy("b", DataRepository.Order.ASC, new ComponentTextType());
        Map<String, fr.inra.oresing.domain.checker.type.FieldType<?>> row = Map.of(
                "a", StringType.getStringTypeFromStringValue("hello"),
                "b", StringType.getStringTypeFromStringValue("world")
        );
        List<String> csv = builder.getCsvRow(List.of(), row, List.of(colA, colB));
        assertThat(csv).containsExactly("hello", "world");
    }

    @Test
    @DisplayName("getCsvRow() avec colonne absente retourne chaîne vide pour la colonne manquante")
    void getCsvRowMissingColumnReturnsEmpty() {
        DataCsvRowBuilder builder = new DataCsvRowBuilder("fr", null, null, false);
        ComponentOrderBy colMissing = new ComponentOrderBy("missing", DataRepository.Order.ASC, new ComponentTextType());
        List<String> csv = builder.getCsvRow(List.of(), Map.of(), List.of(colMissing));
        assertThat(csv).containsExactly("");
    }

    @Test
    @DisplayName("getCsvRow() avec liste vide de colonnes retourne liste vide")
    void getCsvRowNoColumnsReturnsEmptyList() {
        DataCsvRowBuilder builder = new DataCsvRowBuilder("en", null, null, true);
        List<String> csv = builder.getCsvRow(List.of(), Map.of(), List.of());
        assertThat(csv).isEmpty();
    }

    @Test
    @DisplayName("accesseurs du record retournent les valeurs fournies")
    void accessors() {
        DataCsvRowBuilder builder = new DataCsvRowBuilder("de", null, null, true);
        assertThat(builder.language()).isEqualTo("de");
        assertThat(builder.dataRepository()).isNull();
        assertThat(builder.dataDescription()).isNull();
        assertThat(builder.horizontalDisplay()).isTrue();
    }
}
