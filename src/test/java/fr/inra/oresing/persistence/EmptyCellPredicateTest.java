package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pure ( pas de Spring , pas de DB ) pour la classe
 * utilitaire {@link EmptyCellPredicate} . Garantit que la sémantique
 * "cellule vide" reste cohérente entre les deux facettes Java + SQL
 * et qu'elle ne régresse pas si quelqu'un modifie la classe sans
 * comprendre l'impact transverse ( DataRepository.getColumnDistinctValues
 * + DataRepository.getColumnHasEmpty ) .
 */
class EmptyCellPredicateTest {

    @Nested
    class JavaPredicate {

        @Test
        void nullEstConsidereVide() {
            assertTrue(EmptyCellPredicate.isEmpty(null));
        }

        @Test
        void chaineVideEstConsidereeVide() {
            assertTrue(EmptyCellPredicate.isEmpty(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ref2_A titre", "0", " ", "  ", "null", "(vide)"})
        void chaineNonVideNEstPasConsidereeVide(final String value) {
            assertFalse(EmptyCellPredicate.isEmpty(value),
                    "La chaine '" + value + "' ne doit PAS etre consideree vide");
        }
    }

    @Nested
    class SqlPredicate {

        @Test
        void onePredicatDetecteNullEtChaineVide() {
            final String predicate = EmptyCellPredicate.sqlPredicate(Multiplicity.ONE);
            assertAll("predicat ONE",
                    () -> assertNotNull(predicate),
                    () -> assertTrue(predicate.contains("IS NULL"),
                            "doit detecter NULL"),
                    () -> assertTrue(predicate.contains("= ''"),
                            "doit detecter chaine vide"),
                    () -> assertTrue(predicate.startsWith("(") && predicate.endsWith(")"),
                            "doit etre parenthese pour combiner en clause WHERE"));
        }

        @Test
        void manyPredicatDetecteTableauVideEtElementsVides() {
            final String predicate = EmptyCellPredicate.sqlPredicate(Multiplicity.MANY);
            assertAll("predicat MANY",
                    () -> assertNotNull(predicate),
                    () -> assertTrue(predicate.contains("jsonb_array_length"),
                            "doit verifier la longueur du tableau"),
                    () -> assertTrue(predicate.contains("'null'::jsonb"),
                            "doit detecter les elements JSON null"),
                    () -> assertTrue(predicate.contains("'\"\"'::jsonb"),
                            "doit detecter les elements JSON chaine vide"),
                    () -> assertTrue(predicate.contains("jsonb_array_elements"),
                            "doit deplier le tableau JSON"));
        }

        @Test
        void onePredicatUtiliseLeParamComponentKey() {
            assertTrue(EmptyCellPredicate.sqlPredicate(Multiplicity.ONE)
                    .contains(":componentKey"));
        }

        @Test
        void manyPredicatUtiliseLeParamComponentKey() {
            assertTrue(EmptyCellPredicate.sqlPredicate(Multiplicity.MANY)
                    .contains(":componentKey"));
        }
    }

    @Nested
    class JsonPathEmptyPredicate {

        @Test
        void detecteJsonNullEtChaineJsonVide() {
            final String pred = EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE;
            assertAll("predicat jsonpath",
                    () -> assertTrue(pred.contains("@ == null"),
                            "doit detecter JSON null"),
                    () -> assertTrue(pred.contains("@ == \"\""),
                            "doit detecter chaine JSON vide"),
                    () -> assertTrue(pred.contains("||"),
                            "doit composer les 2 cas en OR"));
        }
    }

    @Nested
    class JsonPathConstants {

        @Test
        void jsonPathOneCibleRefvaluesEtComponentKey() {
            assertEquals("rv.refvalues #>> ARRAY[:componentKey]", EmptyCellPredicate.JSON_PATH_ONE);
        }

        @Test
        void jsonUnfoldManyExtraitElementsTexteAvecCoalesce() {
            final String unfold = EmptyCellPredicate.JSON_UNFOLD_MANY;
            assertAll("JSON_UNFOLD_MANY",
                    () -> assertTrue(unfold.contains("jsonb_array_elements_text"),
                            "doit deplier en text scalar"),
                    () -> assertTrue(unfold.contains("COALESCE"),
                            "doit gerer le cas tableau absent ( -> '[]' )"),
                    () -> assertTrue(unfold.contains(":componentKey"),
                            "doit utiliser le param componentKey"));
        }
    }
}
