package fr.inra.oresing.persistence.requestbuilder.data;

import fr.inra.oresing.persistence.EmptyCellPredicate;
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

    // ─── Convention "(vide)" : filtre = null -> JSONPath cellule vide ─────────
    // Mise à jour ticket #519 ( commit fb0fb5d9 ) : le predicat couvre
    // maintenant à la fois JSON null ET chaîne JSON vide "" via la
    // constante centralisée EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE .
    // Les cellules CSV vides sont stockées comme "" dans le JSONB ; sans
    // cette mise à jour la sélection "(vide)" ramenait 0 résultat .

    @Test
    void buildEqualityPredicate_returnsEmptyPredicateForNullValue() {
        // Décision §5.7 : null sur le wire signifie "valeur absente / vide" .
        // Le JSONPath produit délègue à la sentinelle centralisée pour
        // matcher à la fois `@ == null` ( cellule JSON null ) ET
        // `@ == ""` ( cellule CSV vide stockée comme chaîne vide ) .
        Assertions.assertEquals(EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE,
                DataRequestBuilder.buildEqualityPredicate(null));
    }

    @Test
    void buildEqualityPredicate_quotesAndEscapesStringValue() {
        Assertions.assertEquals("@ == \"foo\"",
                DataRequestBuilder.buildEqualityPredicate("foo"));
        // Vérifie que le sanitizer est bien appliqué ( évite régression sur
        // le bug % corrigé dans f91481f ).
        Assertions.assertEquals("@ == \"a%%b\"",
                DataRequestBuilder.buildEqualityPredicate("a%b"));
    }

    @Test
    void buildReferencePredicate_handlesHierarchyForNonNull() {
        // Sur les références , l'égalité est complétée d'un `starts with` qui
        // matche les descendants ( "foo" matche "foo" et "foo.bar" ).
        String result = DataRequestBuilder.buildReferencePredicate("foo");
        Assertions.assertTrue(result.contains("@ == \"foo\""), result);
        Assertions.assertTrue(result.contains("@ starts with \"foo.\""), result);
    }

    @Test
    void buildReferencePredicate_returnsEmptyPredicateForNullValue() {
        // Pour null , pas de `starts with` ( une branche d'arbre vide n'a
        // pas de sens ) , on retombe sur la sentinelle centralisée pour
        // couvrir à la fois JSON null ET chaîne JSON vide .
        Assertions.assertEquals(EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE,
                DataRequestBuilder.buildReferencePredicate(null));
    }

    @Test
    void buildRegexpPredicate_handlesNullValue() {
        // Pour null , like_regex n'a pas de sens ; on retombe sur la
        // sentinelle centralisée pour cohérence avec les autres builders .
        Assertions.assertEquals(EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE,
                DataRequestBuilder.buildRegexpPredicate(null));
    }

    @Test
    void buildRegexpPredicate_combinesEqualityAndLikeRegexForNonNull() {
        String result = DataRequestBuilder.buildRegexpPredicate("OPTMix");
        Assertions.assertTrue(result.contains("@ == \"OPTMix\""), result);
        Assertions.assertTrue(result.contains("@ like_regex \"OPTMix\""), result);
        Assertions.assertTrue(result.contains("flag \"i\""), result);
    }
}
