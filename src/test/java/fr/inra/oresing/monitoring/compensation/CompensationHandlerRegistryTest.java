package fr.inra.oresing.monitoring.compensation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link CompensationHandlerRegistry}.
 */
@Tag("domain.model")
@DisplayName("CompensationHandlerRegistry — routage par operationType")
class CompensationHandlerRegistryTest {

    private CompensationHandler handler(String type) {
        CompensationHandler h = mock(CompensationHandler.class);
        when(h.operationType()).thenReturn(type);
        return h;
    }

    @Test
    @DisplayName("handlerFor() retourne le bon handler quand il existe")
    void handlerForPresent() {
        CompensationHandler h = handler("IMPORT_BINARYFILE");
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(List.of(h));

        assertThat(registry.handlerFor("IMPORT_BINARYFILE")).contains(h);
    }

    @Test
    @DisplayName("handlerFor() retourne empty quand le type est inconnu")
    void handlerForAbsent() {
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(List.of());
        assertThat(registry.handlerFor("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("size() retourne le nombre de handlers enregistrés")
    void size() {
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(
                List.of(handler("TYPE_A"), handler("TYPE_B")));
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("constructeur avec liste vide est valide (size = 0)")
    void emptyHandlers() {
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(List.of());
        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("constructeur lève IllegalStateException si deux handlers ont le même type")
    void duplicateTypeThrows() {
        CompensationHandler h1 = handler("DUPLICATE");
        CompensationHandler h2 = handler("DUPLICATE");

        assertThatThrownBy(() -> new CompensationHandlerRegistry(List.of(h1, h2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DUPLICATE");
    }

    @Test
    @DisplayName("handlerFor() avec plusieurs handlers retourne le bon")
    void handlerForMultiple() {
        CompensationHandler h1 = handler("TYPE_A");
        CompensationHandler h2 = handler("TYPE_B");
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(List.of(h1, h2));

        assertThat(registry.handlerFor("TYPE_A")).contains(h1);
        assertThat(registry.handlerFor("TYPE_B")).contains(h2);
        assertThat(registry.handlerFor("TYPE_C")).isEmpty();
    }
}
