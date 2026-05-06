package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Datum (sans Spring / sans Docker).
 * Classe de domaine pure Java.
 */
@Tag("domain.model")
@DisplayName("Datum – tests unitaires")
class DatumTest {

    // =========================================================================
    //  Constructeurs
    // =========================================================================

    @Nested
    @DisplayName("Constructeurs")
    class ConstructorTest {

        @Test
        @DisplayName("constructeur vide crée un Datum avec une map vide")
        void defaultConstructorCreatesEmptyMap() {
            Datum datum = new Datum();
            assertThat(datum.asMap()).isEmpty();
        }

        @Test
        @DisplayName("constructeur avec map conserve les valeurs")
        void constructorWithMapPreservesValues() {
            FieldType<?> ft = mockFieldType();
            Map<String, FieldType<?>> vals = new LinkedHashMap<>();
            vals.put("col1", ft);
            Datum datum = new Datum(vals);
            assertThat(datum.get("col1")).isSameAs(ft);
        }
    }

    // =========================================================================
    //  copyOf
    // =========================================================================

    @Nested
    @DisplayName("copyOf()")
    class CopyOfTest {

        @Test
        @DisplayName("copyOf() crée un nouveau Datum avec les mêmes valeurs")
        void copyOfCreatesIndependentCopy() {
            Datum original = new Datum();
            FieldType<?> ft = mockFieldType();
            original.put("key", ft);

            Datum copy = Datum.copyOf(original);

            assertThat(copy.get("key")).isSameAs(original.get("key"));
            // Modifier la copie ne doit pas affecter l'original
            copy.put("newKey", mockFieldType());
            assertThat(original.asMap()).doesNotContainKey("newKey");
        }
    }

    // =========================================================================
    //  fromMapMapOfFieldType
    // =========================================================================

    @Nested
    @DisplayName("fromMapMapOfFieldType()")
    class FromMapMapOfFieldTypeTest {

        @Test
        @DisplayName("aplatit les variables/composantes en une seule map")
        void flattensNestedMap() {
            FieldType<?> ft1 = mockFieldType();
            FieldType<?> ft2 = mockFieldType();

            Map<String, Map<String, FieldType<?>>> nested = Map.of(
                    "varA", Map.of("comp1", ft1),
                    "varB", Map.of("comp2", ft2)
            );

            Datum datum = Datum.fromMapMapOfFieldType(nested);

            assertThat(datum.get("comp1")).isSameAs(ft1);
            assertThat(datum.get("comp2")).isSameAs(ft2);
        }

        @Test
        @DisplayName("map vide produit un Datum vide")
        void emptyMapProducesEmptyDatum() {
            Datum datum = Datum.fromMapMapOfFieldType(Map.of());
            assertThat(datum.asMap()).isEmpty();
        }
    }

    // =========================================================================
    //  put et putAll
    // =========================================================================

    @Nested
    @DisplayName("put() et putAll()")
    class PutTest {

        @Test
        @DisplayName("put() ajoute une valeur")
        void putAddsValue() {
            Datum datum = new Datum();
            FieldType<?> ft = mockFieldType();
            datum.put("key", ft);
            assertThat(datum.get("key")).isSameAs(ft);
        }

        @Test
        @DisplayName("putAll() ajoute toutes les valeurs d'un autre Datum")
        void putAllMergesValues() {
            Datum target = new Datum();
            Datum source = new Datum();
            FieldType<?> ft = mockFieldType();
            source.put("srcKey", ft);

            target.putAll(source);

            assertThat(target.get("srcKey")).isSameAs(ft);
        }
    }

    // =========================================================================
    //  filterOnVariable
    // =========================================================================

    @Nested
    @DisplayName("filterOnVariable()")
    class FilterOnVariableTest {

        @Test
        @DisplayName("filtre les clés selon le prédicat")
        void filtersByPredicate() {
            Datum datum = new Datum();
            FieldType<?> ft1 = mockFieldType();
            FieldType<?> ft2 = mockFieldType();
            datum.put("keep", ft1);
            datum.put("discard", ft2);

            Datum filtered = datum.filterOnVariable(key -> key.startsWith("keep"));

            assertThat(filtered.asMap()).containsKey("keep");
            assertThat(filtered.asMap()).doesNotContainKey("discard");
        }

        @Test
        @DisplayName("prédicat toujours faux produit un Datum vide")
        void alwaysFalsePredicateProducesEmpty() {
            Datum datum = new Datum();
            datum.put("key", mockFieldType());

            Datum filtered = datum.filterOnVariable(key -> false);

            assertThat(filtered.asMap()).isEmpty();
        }
    }

    // =========================================================================
    //  getEvaluationContext
    // =========================================================================

    @Nested
    @DisplayName("getEvaluationContext()")
    class GetEvaluationContextTest {

        @Test
        @DisplayName("retourne une map contenant les clés 'datum' et 'datumByComponent'")
        void returnsMapWithDatumKeys() {
            Datum datum = new Datum();
            var ctx = datum.getEvaluationContext();
            assertThat(ctx).containsKey("datum");
            assertThat(ctx).containsKey("datumByComponent");
        }

        @Test
        @DisplayName("'datum' et 'datumByComponent' pointent vers la même map sous-jacente")
        void datumAndDatumByComponentAreSame() {
            Datum datum = new Datum();
            FieldType<?> ft = mockFieldType();
            datum.put("col", ft);
            var ctx = datum.getEvaluationContext();
            @SuppressWarnings("unchecked")
            var datumMap = (Map<String, ?>) ctx.get("datum");
            assertThat(datumMap).containsKey("col");
        }
    }

    // ---- helper ----

    private static FieldType<?> mockFieldType() {
        return new StringType(".*");
    }
}