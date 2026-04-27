package fr.inra.oresing.persistence.requestbuilder.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires de {@link DataRequestBuilder}.
 *
 * <p>Couvre l'échappement des valeurs utilisateur destinées à atterrir dans
 * un littéral chaîne JSONPath ( {@code @ == "<valeur>"} ) , avant que la
 * SQL résultante ne traverse un second {@link String#formatted} dans
 * {@link SelectRequest#build()}.
 */
@Tag("core.config")
class DataRequestBuilderTest {

    @Test
    void sanitizeJsonPathStringValue_passesThroughBenignValue() {
        Assertions.assertEquals("OPTMix_O12_x",
                DataRequestBuilder.sanitizeJsonPathStringValue("OPTMix_O12_x"));
    }

    @Test
    void sanitizeJsonPathStringValue_doublesPercentToSurviveOuterFormatted() {
        // Bug observé : un % saisi par l'utilisateur déclenchait une
        // UnknownFormatConversionException au second .formatted() ( -> 500 ).
        // Le doublage permet au % de ressortir littéral après le format.
        Assertions.assertEquals("OPTMix_O12_%%",
                DataRequestBuilder.sanitizeJsonPathStringValue("OPTMix_O12_%"));

        // Vérification end-to-end : après un nouveau .formatted() ( qui
        // simule SelectRequest.build() ) , le pourcent réapparait littéral.
        String escaped = DataRequestBuilder.sanitizeJsonPathStringValue("a%b%c");
        String afterOuterFormatted = ("@ == \"" + escaped + "\"").formatted();
        Assertions.assertEquals("@ == \"a%b%c\"", afterOuterFormatted);
    }

    @Test
    void sanitizeJsonPathStringValue_escapesDoubleQuoteForJsonPathString() {
        // Sans échappement, un " utilisateur casse @ == "..." -> erreur de
        // parsing JSONPath côté Postgres -> 500.
        Assertions.assertEquals("a\\\"b",
                DataRequestBuilder.sanitizeJsonPathStringValue("a\"b"));
    }

    @Test
    void sanitizeJsonPathStringValue_escapesBackslashBeforeOtherEscapes() {
        // Backslash doit être doublé en premier : sinon les \ ajoutés pour
        // échapper " seraient à leur tour redoublés.
        Assertions.assertEquals("a\\\\b\\\"c",
                DataRequestBuilder.sanitizeJsonPathStringValue("a\\b\"c"));
    }

    @Test
    void sanitizeJsonPathStringValue_escapesSingleQuoteForSqlLiteral() {
        // Le JSONPath est lui-même placé dans un littéral SQL '...' ;
        // un ' utilisateur doit être doublé pour ne pas le casser.
        Assertions.assertEquals("O''Brien",
                DataRequestBuilder.sanitizeJsonPathStringValue("O'Brien"));
    }

    @Test
    void sanitizeJsonPathStringValue_handlesNull() {
        Assertions.assertNull(DataRequestBuilder.sanitizeJsonPathStringValue(null));
    }

    @Test
    void sanitizeJsonPathStringValue_handlesAllSpecialCharsCombined() {
        // Cas pathologique : un seul appel doit produire une chaîne
        // consommable par .formatted() puis par le parseur JSONPath /
        // littéral SQL sans erreur. Ordre des règles :
        // \ -> \\ , puis " -> \" , puis ' -> '' , puis % -> %% .
        String input = "a%b\"c'd\\e";
        Assertions.assertEquals("a%%b\\\"c''d\\\\e",
                DataRequestBuilder.sanitizeJsonPathStringValue(input));
    }
}
