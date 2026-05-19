package fr.inra.oresing.domain.data.deposit.transformation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link GroovyCacheKey}.
 * Couvre R-P2-4 : détection des expressions non-cacheables + equals/hashCode.
 */
@Tag("domain.model")
class GroovyCacheKeyTest {

    // ─── isCacheable ─────────────────────────────────────────────────────────

    @Test
    void isCacheable_expressionSansCurrentRowNumber_retourneVrai() {
        assertTrue(GroovyCacheKey.isCacheable("datum.site == 'Lyon'"));
    }

    @Test
    void isCacheable_expressionAvecCurrentRowNumber_retourneFaux() {
        assertFalse(GroovyCacheKey.isCacheable("currentRowNumber > 0"));
    }

    @Test
    void isCacheable_expressionContenantCurrentRowNumberDansUnCommentaire_retourneFaux() {
        // Conservatif : même dans un commentaire on refuse le cache
        assertFalse(GroovyCacheKey.isCacheable("// currentRowNumber\ndatum.site"));
    }

    @Test
    void isCacheable_expressionNull_retourneFaux() {
        assertFalse(GroovyCacheKey.isCacheable(null));
    }

    @Test
    void isCacheable_expressionVide_retourneVrai() {
        assertTrue(GroovyCacheKey.isCacheable(""));
    }

    // ─── equals & hashCode ───────────────────────────────────────────────────

    @Test
    void deuxClésAvecMêmesContenusSontÉgales() {
        var key1 = new GroovyCacheKey("expr", Map.of("a", "1"));
        var key2 = new GroovyCacheKey("expr", Map.of("a", "1"));
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void deuxClésAvecExpressionDifférenteSontInégales() {
        var key1 = new GroovyCacheKey("expr1", Map.of("a", "1"));
        var key2 = new GroovyCacheKey("expr2", Map.of("a", "1"));
        assertNotEquals(key1, key2);
    }

    @Test
    void deuxClésAvecInputValuesDifférentsSontInégales() {
        var key1 = new GroovyCacheKey("expr", Map.of("a", "1"));
        var key2 = new GroovyCacheKey("expr", Map.of("a", "2"));
        assertNotEquals(key1, key2);
    }

    @Test
    void clécAvecMapVideEstDifférente() {
        var key1 = new GroovyCacheKey("expr", Map.of());
        var key2 = new GroovyCacheKey("expr", Map.of("a", "1"));
        assertNotEquals(key1, key2);
    }

    @Test
    void clécAvecExpressionNullGéréSansNPE() {
        var key1 = new GroovyCacheKey(null, Map.of());
        var key2 = new GroovyCacheKey(null, Map.of());
        assertEquals(key1, key2);
    }

    @Test
    void equalsAvecMêmeRéférenceRetourneVrai() {
        var key = new GroovyCacheKey("expr", Map.of("a", "1"));
        assertEquals(key, key);
    }

    @Test
    void equalsAvecTypeIncompatibleRetourneFaux() {
        var key = new GroovyCacheKey("expr", Map.of("a", "1"));
        assertNotEquals("not a GroovyCacheKey", key);
    }
}