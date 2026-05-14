package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour {@link ComponentFiltersForWordByRegexp} – validation du compact constructor.
 */
@Tag("domain.model")
@DisplayName("ComponentFiltersForWordByRegexp – validation des paramètres")
class ComponentFiltersForWordByRegexpTest {

    @Test
    @DisplayName("Cas nominal : componentKey non-null, filtres non-vides → OK")
    void validConstruction() {
        ComponentFiltersForWordByRegexp record = new ComponentFiltersForWordByRegexp(
                "myComponent",
                List.of("pattern1", "pattern2"),
                Multiplicity.ONE);
        assertThat(record.componentKey()).isEqualTo("myComponent");
        assertThat(record.filters()).hasSize(2);
    }

    @Test
    @DisplayName("componentKey null → BadDownloadDatasetQuery")
    void nullComponentKeyThrows() {
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp(
                null,
                List.of("p"),
                Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filters null → BadDownloadDatasetQuery")
    void nullFiltersThrows() {
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp(
                "key",
                null,
                Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filters vide → BadDownloadDatasetQuery")
    void emptyFiltersThrows() {
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp(
                "key",
                List.of(),
                Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filtre contenant une chaîne vide → BadDownloadDatasetQuery")
    void emptyStringInFiltersThrows() {
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp(
                "key",
                List.of("valid", ""),
                Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filtre contenant null est accepté (convention vide §5.7)")
    void nullValueInFiltersIsAccepted() {
        // null dans la liste est accepté selon la convention "vide" §5.7
        List<String> filtersWithNull = new java.util.ArrayList<>();
        filtersWithNull.add("valid");
        filtersWithNull.add(null);
        ComponentFiltersForWordByRegexp record = new ComponentFiltersForWordByRegexp(
                "key",
                filtersWithNull,
                Multiplicity.MANY);
        assertThat(record.filters()).hasSize(2);
    }
}
