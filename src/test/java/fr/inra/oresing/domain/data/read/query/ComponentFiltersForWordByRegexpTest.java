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
        ComponentFiltersForWordByRegexp entry = new ComponentFiltersForWordByRegexp(
                "myComponent",
                List.of("pattern1", "pattern2"),
                Multiplicity.ONE);
        assertThat(entry.componentKey()).isEqualTo("myComponent");
        assertThat(entry.filters()).hasSize(2);
    }

    @Test
    @DisplayName("componentKey null → BadDownloadDatasetQuery")
    void nullComponentKeyThrows() {
        List<String> filters = List.of("p");
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp(null, filters, Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filters null → BadDownloadDatasetQuery")
    void nullFiltersThrows() {
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp("key", null, Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filters vide → BadDownloadDatasetQuery")
    void emptyFiltersThrows() {
        List<String> emptyFilters = List.of();
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp("key", emptyFilters, Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filtre contenant une chaîne vide → BadDownloadDatasetQuery")
    void emptyStringInFiltersThrows() {
        List<String> filtersWithEmpty = List.of("valid", "");
        assertThatThrownBy(() -> new ComponentFiltersForWordByRegexp("key", filtersWithEmpty, Multiplicity.ONE))
                .isInstanceOf(BadDownloadDatasetQuery.class);
    }

    @Test
    @DisplayName("filtre contenant null est accepté (convention vide §5.7)")
    void nullValueInFiltersIsAccepted() {
        // null dans la liste est accepté selon la convention "vide" §5.7
        List<String> filtersWithNull = new java.util.ArrayList<>();
        filtersWithNull.add("valid");
        filtersWithNull.add(null);
        ComponentFiltersForWordByRegexp entry = new ComponentFiltersForWordByRegexp(
                "key",
                filtersWithNull,
                Multiplicity.MANY);
        assertThat(entry.filters()).hasSize(2);
    }
}