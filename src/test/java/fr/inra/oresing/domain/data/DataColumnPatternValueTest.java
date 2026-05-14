package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour DataColumnPatternValue (sans Spring / Docker).
 */
@Tag("domain.model")
@DisplayName("DataColumnPatternValue – tests unitaires")
class DataColumnPatternValueTest {

    // =========================================================================
    //  Constructeurs
    // =========================================================================

    @Nested
    @DisplayName("Constructeurs")
    class ConstructorsTest {

        @Test
        @DisplayName("constructeur avec PatternType initialise la map")
        void constructorWithPatternType() {
            Map<String, FieldType<?>> innerMap = new HashMap<>();
            innerMap.put("key1", StringType.getStringTypeFromStringValue("val1"));
            PatternType<String, FieldType<?>> patternType = new PatternType<>(innerMap);
            DataColumnPatternValue dcpv = new DataColumnPatternValue((FieldType<?>) patternType);
            assertThat(dcpv.values()).isNotNull();
        }

        @Test
        @DisplayName("constructeur avec FieldType non-PatternType donne une map vide")
        void constructorWithNonPatternType() {
            DataColumnPatternValue dcpv = new DataColumnPatternValue(StringType.getStringTypeFromStringValue("v"));
            assertThat(dcpv.values()).isEmpty();
        }

        @Test
        @DisplayName("constructeur avec null donne une map vide")
        void constructorWithNull() {
            DataColumnPatternValue dcpv = new DataColumnPatternValue((FieldType<?>) null);
            assertThat(dcpv.values()).isEmpty();
        }

        @Test
        @DisplayName("constructeur avec Map directe crée les valeurs")
        void constructorWithMap() {
            Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
            DataColumn dc = new DataColumn("col1");
            values.put(dc, new DataColumnSingleValue(StringType.getStringTypeFromStringValue("val")));
            DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
            assertThat(dcpv.values()).hasSize(1);
        }
    }

    // =========================================================================
    //  getValuesToCheck
    // =========================================================================

    @Test
    @DisplayName("getValuesToCheck retourne un PatternType")
    void getValuesToCheck() {
        Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
        DataColumn dc = new DataColumn("col1");
        values.put(dc, new DataColumnSingleValue(StringType.getStringTypeFromStringValue("v")));
        DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
        FieldType<?> result = dcpv.getValuesToCheck();
        assertThat(result).isInstanceOf(PatternType.class);
    }

    // =========================================================================
    //  toJsonForDatabase / toJsonForFrontend
    // =========================================================================

    @Nested
    @DisplayName("toJsonForDatabase / toJsonForFrontend")
    class JsonTest {

        @Test
        @DisplayName("toJsonForDatabase retourne une map String -> Object")
        void toJsonForDatabase() {
            Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
            values.put(new DataColumn("c1"),
                    new DataColumnSingleValue(StringType.getStringTypeFromStringValue("hello")));
            DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
            Map<String, Object> result = dcpv.toJsonForDatabase();
            assertThat(result).containsKey("c1");
        }

        @Test
        @DisplayName("toJsonForFrontend retourne une map String -> Object")
        void toJsonForFrontend() {
            Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
            values.put(new DataColumn("c2"),
                    new DataColumnSingleValue(StringType.getStringTypeFromStringValue("world")));
            DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
            Map<String, Object> result = dcpv.toJsonForFrontend();
            assertThat(result).containsKey("c2");
        }

        @Test
        @DisplayName("toJsonForDatabase avec IntegerType retourne la valeur entière")
        void toJsonForDatabaseWithIntegerType() {
            IntegerType intType = IntegerType.of(42);
            Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
            values.put(new DataColumn("num"), new DataColumnSingleValue(intType));
            DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
            Map<String, Object> result = dcpv.toJsonForDatabase();
            assertThat(result.get("num")).isEqualTo(42);
        }

        @Test
        @DisplayName("toJsonForDatabase avec BooleanType retourne la valeur booléenne")
        void toJsonForDatabaseWithBooleanType() {
            BooleanType boolType = new BooleanType(true);
            Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
            values.put(new DataColumn("flag"), new DataColumnSingleValue(boolType));
            DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
            Map<String, Object> result = dcpv.toJsonForDatabase();
            assertThat(result.get("flag")).isEqualTo(true);
        }
    }

    // =========================================================================
    //  put
    // =========================================================================

    @Test
    @DisplayName("put ajoute une entrée dans la map")
    void put() {
        Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
        DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
        DataColumn dc = new DataColumn("added");
        dcpv.put(dc, new DataColumnSingleValue(StringType.getStringTypeFromStringValue("v")));
        assertThat(dcpv.values()).containsKey(dc);
    }

    // =========================================================================
    //  toObjectsExposedInGroovyContext
    // =========================================================================

    @Test
    @DisplayName("toObjectsExposedInGroovyContext retourne une map clé -> valeur sérialisée")
    void toObjectsExposedInGroovyContext() {
        Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
        values.put(new DataColumn("g"),
                new DataColumnSingleValue(StringType.getStringTypeFromStringValue("groovy")));
        DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
        Map<String, Object> ctx = dcpv.toObjectsExposedInGroovyContext();
        assertThat(ctx).isNotNull();
        assertThat(ctx).containsKey("g");
    }

    @Test
    @DisplayName("toObjectsExposedInGroovyContext avec valeur null retourne null pour la clé")
    void toObjectsExposedInGroovyContextWithNull() {
        Map<DataColumn, DataColumnValue<?, ?>> values = new LinkedHashMap<>();
        values.put(new DataColumn("empty"), null);
        DataColumnPatternValue dcpv = new DataColumnPatternValue(values);
        Map<String, Object> ctx = dcpv.toObjectsExposedInGroovyContext();
        assertThat(ctx).containsKey("empty");
        assertThat(ctx.get("empty")).isNull();
    }

    // =========================================================================
    //  Column.__VALUE__ scenario
    // =========================================================================

    @Test
    @DisplayName("constructeur avec PatternType contenant __VALUE__ est créé")
    void constructorWithValueKey() {
        Map<String, FieldType<?>> innerMap = new HashMap<>();
        innerMap.put(Column.__VALUE__, StringType.getStringTypeFromStringValue("test"));
        PatternType<String, FieldType<?>> patternType = new PatternType<>(innerMap);
        DataColumnPatternValue dcpv = new DataColumnPatternValue((FieldType<?>) patternType);
        assertThat(dcpv.values()).isNotNull();
    }
}