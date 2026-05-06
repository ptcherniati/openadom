package fr.inra.oresing.domain.groovy.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour GroovyException.
 */
@Tag("domain.model")
@DisplayName("GroovyException – constructeurs et accesseurs")
class GroovyExceptionTest {

    @Test
    @DisplayName("constructeur(message) crée une exception avec params vide")
    void constructorWithMessageOnly() {
        GroovyException ex = new GroovyException("BAD_VALUE");
        assertThat(ex.getMessage()).isEqualTo("BAD_VALUE");
        assertThat(ex.getParams()).isEmpty();
        assertThat(ex.getParamsCopy()).isEmpty();
    }

    @Test
    @DisplayName("constructeur(message, null) traite null comme map vide")
    void constructorWithNullParams() {
        GroovyException ex = new GroovyException("ERR", null);
        assertThat(ex.getParams()).isEmpty();
        assertThat(ex.getParamsCopy()).isEmpty();
    }

    @Test
    @DisplayName("constructeur(message, params) conserve les paramètres")
    void constructorWithParams() {
        Map<String, Object> params = Map.of("key1", "val1", "key2", 42);
        GroovyException ex = new GroovyException("ERR", params);
        assertThat(ex.getParams()).containsEntry("key1", "val1");
        assertThat(ex.getParams()).containsEntry("key2", 42);
    }

    @Test
    @DisplayName("getParamsCopy() retourne une copie immuable incluant les params")
    void getParamsCopy() {
        Map<String, Object> params = Map.of("reason", "bad input");
        GroovyException ex = new GroovyException("ERR", params);
        Map<String, Object> copy = ex.getParamsCopy();
        assertThat(copy).containsEntry("reason", "bad input");
    }

    @Test
    @DisplayName("DEFAULT_MESSAGE est défini")
    void defaultMessageConstant() {
        assertThat(GroovyException.DEFAULT_MESSAGE).isEqualTo("BAD_VALUE_FOR_EXPRESSION");
    }

    @Test
    @DisplayName("GroovyException est une RuntimeException")
    void isRuntimeException() {
        GroovyException ex = new GroovyException("test");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}