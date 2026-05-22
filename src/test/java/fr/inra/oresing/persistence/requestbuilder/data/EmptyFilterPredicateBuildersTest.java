package fr.inra.oresing.persistence.requestbuilder.data;

import fr.inra.oresing.persistence.EmptyCellPredicate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires des 3 builders de predicats jsonpath
 * ( {@link DataRequestBuilder#buildEqualityPredicate} ,
 * {@link DataRequestBuilder#buildReferencePredicate} ,
 * {@link DataRequestBuilder#buildRegexpPredicate} ) .
 *
 * <p>Garantit qu'ils delèguent tous a la meme source de verite
 * {@link EmptyCellPredicate#JSONPATH_EMPTY_PREDICATE} pour le cas
 * {@code filter == null} ( sentinelle "( vide )" ) et qu'ils generent
 * le bon predicat jsonpath pour les valeurs non-vides .
 *
 * <p>Ces tests sont les garants que la regression du ticket #519
 * ( images #116-#118 : selection "( vide )" ramenait 0 résultat car
 * le predicat etait {@code @ == null} qui ne matchait que JSON null
 * et pas la chaine JSON vide {@code ""} ) ne revient pas si quelqu'un
 * remplace {@code JSONPATH_EMPTY_PREDICATE} par {@code "@ == null"}
 * en revertant accidentellement .
 */
@Tag("core.config")
class EmptyFilterPredicateBuildersTest {

    @Nested
    class EqualityPredicate {

        @Test
        void filterNullDelegueALaSentinelleEmptyCellPredicate() {
            assertEquals(
                    EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE,
                    DataRequestBuilder.buildEqualityPredicate(null),
                    "filter null doit produire le predicat 'cellule vide' centralisé"
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"ref2_A titre", "humidite_volumique_du_sol", "0"})
        void filterNonNullProduitEgaliteJsonpath(final String filter) {
            final String pred = DataRequestBuilder.buildEqualityPredicate(filter);
            assertAll("predicat egalite",
                    () -> assertNotNull(pred),
                    () -> assertTrue(pred.startsWith("@ == "), "doit commencer par '@ =='"),
                    () -> assertTrue(pred.contains(filter),
                            "doit contenir le filter '" + filter + "'"));
        }
    }

    @Nested
    class ReferencePredicate {

        @Test
        void filterNullDelegueALaSentinelleEmptyCellPredicate() {
            assertEquals(
                    EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE,
                    DataRequestBuilder.buildReferencePredicate(null),
                    "filter null doit produire le predicat 'cellule vide' centralisé"
            );
        }

        @Test
        void filterNonNullProduitEgaliteEtMatchHierarchique() {
            final String pred = DataRequestBuilder.buildReferencePredicate("Parent");
            assertAll("predicat reference",
                    () -> assertNotNull(pred),
                    () -> assertTrue(pred.contains("@ == \"Parent\""),
                            "doit matcher la valeur exacte"),
                    () -> assertTrue(pred.contains("starts with \"Parent.\""),
                            "doit matcher les descendants hierarchiques"));
        }
    }

    @Nested
    class RegexpPredicate {

        @Test
        void filterNullDelegueALaSentinelleEmptyCellPredicate() {
            assertEquals(
                    EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE,
                    DataRequestBuilder.buildRegexpPredicate(null),
                    "filter null doit produire le predicat 'cellule vide' centralisé"
            );
        }

        @Test
        void filterNonNullProduitEgaliteEtRegexpInsensibleCasse() {
            final String pred = DataRequestBuilder.buildRegexpPredicate("regia");
            assertAll("predicat regexp",
                    () -> assertNotNull(pred),
                    () -> assertTrue(pred.contains("@ == \"regia\""),
                            "doit matcher la valeur exacte"),
                    () -> assertTrue(pred.contains("like_regex \"regia\""),
                            "doit utiliser like_regex pour LIKE partiel"),
                    () -> assertTrue(pred.contains("flag \"i\""),
                            "doit etre insensible a la casse"));
        }
    }

    @Nested
    class CoherenceEntreBuilders {

        /**
         * Invariant : les 3 builders DOIVENT produire le meme predicat
         * pour {@code filter == null} ( convention "(vide)" ) . Si l'un
         * diverge , la regression du ticket #519 reapparait : le bouton
         * "( vide )" cliqué dans certains types de filtres ramène les
         * lignes attendues , dans d'autres pas .
         */
        @Test
        void les3BuildersUtilisentLaMemeSentinelleVide() {
            final String eq = DataRequestBuilder.buildEqualityPredicate(null);
            final String ref = DataRequestBuilder.buildReferencePredicate(null);
            final String regex = DataRequestBuilder.buildRegexpPredicate(null);
            assertAll("coherence cellule vide",
                    () -> assertEquals(eq, ref, "Equality vs Reference"),
                    () -> assertEquals(eq, regex, "Equality vs Regexp"),
                    () -> assertEquals(EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE, eq,
                            "doit utiliser la constante centralisée"));
        }
    }
}
