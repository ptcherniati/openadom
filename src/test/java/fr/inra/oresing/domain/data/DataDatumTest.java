package fr.inra.oresing.domain.data;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.application.configuration.Ltree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("domain.model")
@DisplayName("DataDatum – tests unitaires")
class DataDatumTest {

    private static DataColumn col(String name) {
        return new DataColumn(name);
    }

    private static DataColumnSingleValue strVal(String v) {
        return new DataColumnSingleValue(StringType.getStringTypeFromStringValue(v));
    }

    // =========================================================================
    //  Constructeurs
    // =========================================================================

    @Nested
    @DisplayName("Constructeurs")
    class ConstructorsTest {

        @Test
        @DisplayName("constructeur vide crée un DataDatum sans valeurs")
        void emptyConstructor() {
            DataDatum datum = new DataDatum();
            assertThat(datum.values()).isEmpty();
        }

        @Test
        @DisplayName("constructeur avec map initialise les valeurs")
        void constructorWithMap() {
            Map<DataColumn, DataColumnValue> map = new LinkedHashMap<>();
            map.put(col("col1"), strVal("val1"));
            DataDatum datum = new DataDatum(map);
            assertThat(datum.values()).hasSize(1);
        }
    }

    // =========================================================================
    //  copyOf
    // =========================================================================

    @Test
    @DisplayName("copyOf crée une copie indépendante")
    void copyOf() {
        DataDatum original = new DataDatum();
        original.put(col("a"), strVal("x"));
        DataDatum copy = DataDatum.copyOf(original);
        assertThat(copy.values()).hasSize(1);
        // modification de l'original ne doit pas affecter la copie
        original.put(col("b"), strVal("y"));
        assertThat(copy.values()).hasSize(1);
    }

    // =========================================================================
    //  fromDatabaseJson
    // =========================================================================

    @Nested
    @DisplayName("fromDatabaseJson")
    class FromDatabaseJsonTest {

        @Test
        @DisplayName("avec valeur String simple")
        void fromDatabaseJsonStringValue() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("col1", "hello");
            DataDatum datum = DataDatum.fromDatabaseJson(json);
            assertThat(datum.values()).hasSize(1);
            assertThat(datum.values().get(col("col1"))).isInstanceOf(DataColumnSingleValue.class);
        }

        @Test
        @DisplayName("avec valeur null")
        void fromDatabaseJsonNullValue() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("col1", null);
            DataDatum datum = DataDatum.fromDatabaseJson(json);
            assertThat(datum.values().get(col("col1"))).isInstanceOf(DataColumnSingleValue.class);
        }

        @Test
        @DisplayName("avec valeur Map (indexed)")
        void fromDatabaseJsonMapValue() {
            Map<String, Object> json = new LinkedHashMap<>();
            Map<String, String> innerMap = new LinkedHashMap<>();
            innerMap.put("ref.key", "val");
            json.put("col1", innerMap);
            DataDatum datum = DataDatum.fromDatabaseJson(json);
            assertThat(datum.values().get(col("col1"))).isInstanceOf(DataColumnIndexedValue.class);
        }

        @Test
        @DisplayName("avec valeur Collection (multiple values)")
        void fromDatabaseJsonCollectionValue() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("col1", List.of("a", "b"));
            DataDatum datum = DataDatum.fromDatabaseJson(json);
            assertThat(datum.values().get(col("col1"))).isInstanceOf(DataColumnMultipleValue.class);
        }
    }

    // =========================================================================
    //  contains / get
    // =========================================================================

    @Nested
    @DisplayName("contains et get")
    class ContainsGetTest {

        @Test
        @DisplayName("contains retourne true pour une colonne existante")
        void containsExisting() {
            DataDatum datum = new DataDatum();
            datum.put(col("name"), strVal("Alice"));
            assertThat(datum.contains(col("name"))).isTrue();
        }

        @Test
        @DisplayName("contains retourne false pour une colonne inconnue")
        void containsUnknown() {
            DataDatum datum = new DataDatum();
            assertThat(datum.contains(col("unknown"))).isFalse();
        }

        @Test
        @DisplayName("get retourne la valeur pour une colonne existante")
        void getExisting() {
            DataDatum datum = new DataDatum();
            datum.put(col("x"), strVal("42"));
            DataColumnValue val = datum.get(col("x"));
            assertThat(val).isNotNull();
        }

        @Test
        @DisplayName("get lève une exception pour une colonne manquante")
        void getMissing() {
            DataDatum datum = new DataDatum();
            assertThatThrownBy(() -> datum.get(col("absent")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    //  put / putAll
    // =========================================================================

    @Nested
    @DisplayName("put / putAll")
    class PutTest {

        @Test
        @DisplayName("put ajoute une valeur")
        void put() {
            DataDatum datum = new DataDatum();
            datum.put(col("k"), strVal("v"));
            assertThat(datum.contains(col("k"))).isTrue();
        }

        @Test
        @DisplayName("put remplace par valeur du même type")
        void putSameType() {
            DataDatum datum = new DataDatum();
            datum.put(col("k"), strVal("v1"));
            datum.put(col("k"), strVal("v2"));
            assertThat(datum.values()).hasSize(1);
        }

        @Test
        @DisplayName("putAll fusionne deux DataDatum")
        void putAll() {
            DataDatum d1 = new DataDatum();
            d1.put(col("a"), strVal("1"));
            DataDatum d2 = new DataDatum();
            d2.put(col("b"), strVal("2"));
            d1.putAll(d2);
            assertThat(d1.values()).hasSize(2);
        }
    }

    // =========================================================================
    //  toJsonForDatabase / toJsonForFrontend
    // =========================================================================

    @Nested
    @DisplayName("toJsonForDatabase / toJsonForFrontend")
    class JsonTest {

        @Test
        @DisplayName("toJsonForDatabase retourne une ImmutableMap")
        void toJsonForDatabase() {
            DataDatum datum = new DataDatum();
            datum.put(col("c1"), strVal("hello"));
            ImmutableMap<String, ?> json = datum.toJsonForDatabase();
            assertThat(json).containsKey("c1");
        }

        @Test
        @DisplayName("toJsonForFrontend retourne une map")
        void toJsonForFrontend() {
            DataDatum datum = new DataDatum();
            datum.put(col("c2"), strVal("world"));
            Map<String, ?> json = datum.toJsonForFrontend();
            assertThat(json).containsKey("c2");
        }

        @Test
        @DisplayName("toJsonForDatabase avec DataColumnIndexedValue")
        void toJsonForDatabaseWithIndexedValue() {
            Map<Ltree, String> indexed = new LinkedHashMap<>();
            indexed.put(Ltree.fromSql("ref.key"), "val");
            DataDatum datum = new DataDatum();
            datum.put(col("idx"), new DataColumnIndexedValue(indexed));
            ImmutableMap<String, ?> json = datum.toJsonForDatabase();
            assertThat(json).containsKey("idx");
        }
    }

    // =========================================================================
    //  filterHidden
    // =========================================================================

    @Test
    @DisplayName("filterHidden supprime les colonnes cachées")
    void filterHidden() {
        DataDatum datum = new DataDatum();
        datum.put(col("visible"), strVal("yes"));
        datum.put(col("hidden"), strVal("no"));
        DataDatum filtered = datum.filterHidden(Set.of("hidden"));
        assertThat(filtered.values()).hasSize(1);
        assertThat(filtered.contains(col("visible"))).isTrue();
        assertThat(filtered.contains(col("hidden"))).isFalse();
    }

    // =========================================================================
    //  with
    // =========================================================================

    @Test
    @DisplayName("with fusionne deux DataDatum dans un nouveau")
    void with() {
        DataDatum d1 = new DataDatum();
        d1.put(col("a"), strVal("1"));
        DataDatum d2 = new DataDatum();
        d2.put(col("b"), strVal("2"));
        DataDatum merged = d1.with(d2);
        assertThat(merged.values()).hasSize(2);
        // d1 et d2 inchangés
        assertThat(d1.values()).hasSize(1);
        assertThat(d2.values()).hasSize(1);
    }

    // =========================================================================
    //  getEvaluationContext / toObjectsExposedInGroovyContext
    // =========================================================================

    @Test
    @DisplayName("getEvaluationContext contient la clé 'datum'")
    void getEvaluationContext() {
        DataDatum datum = new DataDatum();
        datum.put(col("x"), strVal("val"));
        ImmutableMap<String, Object> ctx = datum.getEvaluationContext();
        assertThat(ctx).containsKey("datum");
    }

    @Test
    @DisplayName("toObjectsExposedInGroovyContext retourne une map")
    void toObjectsExposedInGroovyContext() {
        DataDatum datum = new DataDatum();
        datum.put(col("g"), strVal("groovy"));
        ImmutableMap<String, Object> ctx = datum.toObjectsExposedInGroovyContext();
        assertThat(ctx).containsKey("g");
    }

    // =========================================================================
    //  getValuesToCheck
    // =========================================================================

    @Test
    @DisplayName("getValuesToCheck retourne la valeur de la colonne")
    void getValuesToCheck() {
        DataDatum datum = new DataDatum();
        datum.put(col("t"), strVal("check"));
        assertThat(datum.getValuesToCheck(col("t"))).isNotNull();
    }
}