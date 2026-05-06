package fr.inra.oresing.domain.data.deposit.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les constantes et méthodes statiques de DataImporterContext.
 */
@Tag("domain.model")
@DisplayName("DataImporterContext – constantes statiques")
class DataImporterContextStaticTest {

    @Test
    @DisplayName("COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR vaut '__'")
    void compositeSeparatorConstant() {
        assertThat(DataImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR).isEqualTo("__");
    }

    @Test
    @DisplayName("getCompositeNaturalKeyComponentsSeparator() retourne '__'")
    void getCompositeSeparator() {
        assertThat(DataImporterContext.getCompositeNaturalKeyComponentsSeparator()).isEqualTo("__");
    }
}