package fr.inra.oresing.domain.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour DataColumnDisplayValue.
 */
@Tag("domain.model")
@DisplayName("DataColumnDisplayValue – factory et accesseurs")
class DataColumnDisplayValueTest {

    @Test
    @DisplayName("empty() retourne une instance non nulle avec value null")
    void emptyFactory() {
        DataColumnDisplayValue empty = DataColumnDisplayValue.empty();
        assertThat(empty).isNotNull();
        assertThat(empty.value()).isNull();
    }

    @Test
    @DisplayName("empty() est un singleton (même référence)")
    void emptyIsSingleton() {
        DataColumnDisplayValue a = DataColumnDisplayValue.empty();
        DataColumnDisplayValue b = DataColumnDisplayValue.empty();
        assertThat(a).isSameAs(b);
    }

    @Test
    @DisplayName("getValuesToCheck() retourne null")
    void getValuesToCheck() {
        assertThat(DataColumnDisplayValue.empty().getValuesToCheck()).isNull();
    }

    @Test
    @DisplayName("toJsonForFrontend() retourne null")
    void toJsonForFrontend() {
        assertThat(DataColumnDisplayValue.empty().toJsonForFrontend()).isNull();
    }

    @Test
    @DisplayName("toJsonForDatabase() retourne null")
    void toJsonForDatabase() {
        assertThat(DataColumnDisplayValue.empty().toJsonForDatabase()).isNull();
    }

    @Test
    @DisplayName("transform() retourne null")
    void transform() {
        assertThat(DataColumnDisplayValue.empty().transform(ft -> ft)).isNull();
    }

    @Test
    @DisplayName("toValueString() retourne null")
    void toValueString() {
        assertThat(DataColumnDisplayValue.empty().toValueString(null, "col", "key")).isNull();
    }
}