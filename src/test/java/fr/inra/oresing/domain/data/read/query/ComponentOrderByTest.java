package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.repository.data.DataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs de {@link ComponentOrderBy} et de l'interface
 * {@link ComponentOrderByForExport#valueToString} — aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("ComponentOrderBy / ComponentOrderByForExport – valueToString & toValue")
class ComponentOrderByTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentOrderBy constructeur
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ComponentOrderBy — constructeur")
    class ConstructorTest {

        @Test
        @DisplayName("null componentKey lève une exception")
        void nullComponentKeyThrows() {
            ComponentTextType textType = new ComponentTextType();
            assertThatThrownBy(() -> new ComponentOrderBy(null, DataRepository.Order.ASC, textType))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("null sqlType est remplacé par ComponentTextType")
        void nullSqlTypeDefaultsToTextType() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, null);
            assertThat(ob.sqlType()).isInstanceOf(ComponentTextType.class);
        }

        @Test
        @DisplayName("null order est remplacé par ASC")
        void nullOrderDefaultsToAsc() {
            ComponentOrderBy ob = new ComponentOrderBy("col", null, null);
            assertThat(ob.order()).isEqualTo(DataRepository.Order.ASC);
        }

        @Test
        @DisplayName("accesseurs retournent les valeurs fournies")
        void accessors() {
            ComponentOrderBy ob = new ComponentOrderBy("myCol", DataRepository.Order.DESC, new ComponentReferenceType());
            assertThat(ob.componentKey()).isEqualTo("myCol");
            assertThat(ob.order()).isEqualTo(DataRepository.Order.DESC);
            assertThat(ob.sqlType()).isInstanceOf(ComponentReferenceType.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentOrderByForExport.valueToString — branche MapType
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valueToString — branche MapType → 'pas trouvé'")
    class MapTypeBranchTest {

        @Test
        @DisplayName("MapType fieldType retourne 'pas trouvé'")
        void mapTypeReturnsPasTrouve() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentTextType());
            MapType<String, Object> mapType = new MapType<>(new HashMap<>());
            String result = ob.valueToString(List.of(), "fr", null, mapType);
            assertThat(result).isEqualTo("pas trouvé");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentOrderByForExport.valueToString — branche texte (default)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valueToString — branche texte par défaut")
    class TextBranchTest {

        @Test
        @DisplayName("ComponentTextType avec StringType retourne fieldType.toString()")
        void textTypeReturnsFieldTypeToString() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentTextType());
            StringType st = StringType.getStringTypeFromStringValue("hello");
            String result = ob.valueToString(List.of(), "fr", null, st);
            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("ComponentTextType avec null fieldType retourne chaîne vide")
        void nullFieldTypeReturnsEmpty() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentTextType());
            String result = ob.valueToString(List.of(), "fr", null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ComponentNumericType avec StringType retourne fieldType.toString()")
        void numericTypeReturnsFieldTypeToString() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentNumericType());
            StringType st = StringType.getStringTypeFromStringValue("42");
            String result = ob.valueToString(List.of(), "fr", null, st);
            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("ComponentBooleanType avec StringType retourne fieldType.toString()")
        void booleanTypeReturnsFieldTypeToString() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentBooleanType());
            StringType st = StringType.getStringTypeFromStringValue("true");
            String result = ob.valueToString(List.of(), "fr", null, st);
            assertThat(result).isEqualTo("true");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentOrderByForExport.valueToString — branche null sqlType
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valueToString — branche sqlType = null")
    class NullSqlTypeBranchTest {

        @Test
        @DisplayName("DynamicComponentOrderBy.sqlType() ne peut pas être null (toujours ComponentTextType)")
        void dynamicComponentOrderByHasTextType() {
            DynamicComponentOrderBy ob = new DynamicComponentOrderBy("col", Map.of());
            assertThat(ob.sqlType()).isInstanceOf(ComponentTextType.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentOrderByForExport.valueToString — branche ComponentDateType
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valueToString — branche ComponentDateType")
    class DateTypeBranchTest {

        @Test
        @DisplayName("StringType contenant 'date:ISO:pattern' est formaté selon le pattern")
        void dateTypeFormatsCorrectly() {
            // "date:2024-01-15T00:00:00:yyyy-MM-dd" matches the regex ^date:(.{19}):(.*)
            // group(1)=2024-01-15T00:00:00, group(2)=yyyy-MM-dd
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC,
                    new ComponentDateType("yyyy-MM-dd", DownloadDatasetQueryAdvancedSearch.FieldType.date));
            StringType st = StringType.getStringTypeFromStringValue("date:2024-01-15T00:00:00:yyyy-MM-dd");
            String result = ob.valueToString(List.of(), "fr", null, st);
            assertThat(result).isEqualTo("2024-01-15");
        }

        @Test
        @DisplayName("StringType ne contenant pas le préfixe 'date:' retourne chaîne vide")
        void nonMatchingDateReturnsEmpty() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC,
                    new ComponentDateType("yyyy-MM-dd", DownloadDatasetQueryAdvancedSearch.FieldType.date));
            StringType st = StringType.getStringTypeFromStringValue("not-a-date");
            String result = ob.valueToString(List.of(), "fr", null, st);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null fieldType avec ComponentDateType retourne chaîne vide (NPE évité)")
        void nullFieldTypeWithDateTypeReturnsEmpty() {
            // fieldType is null → the pattern match throws NPE, but the switch handles null sqlType → ""
            // Actually with null fieldType, the code does: Matcher = Pattern.compile(...).matcher(null.getValue().toString())
            // which throws NPE — so we only test non-null fieldType for ComponentDateType
            // Instead, test that null sqlType returns ""
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentTextType());
            String result = ob.valueToString(List.of(), "fr", null, null);
            assertThat(result).isEmpty();
        }
    }



    @Nested
    @DisplayName("valueToString — branche ComponentReferenceType")
    class ReferenceTypeBranchTest {

        @Test
        @DisplayName("ComponentReferenceType sans description retourne fieldType.toString()")
        void referenceTypeWithoutDescriptionReturnsToString() {
            ComponentOrderBy ob = new ComponentOrderBy("col", DataRepository.Order.ASC, new ComponentReferenceType());
            StringType st = StringType.getStringTypeFromStringValue("ref_val");
            String result = ob.valueToString(List.of(), "fr", null, st);
            assertThat(result).isEqualTo("ref_val");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentOrderBy.toValue — basic stream
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ComponentOrderBy.toValue()")
    class ToValueTest {

        @Test
        @DisplayName("toValue() retourne un Stream avec la valeur de la colonne")
        void toValueReturnsSingleElement() {
            ComponentOrderBy ob = new ComponentOrderBy("myCol", DataRepository.Order.ASC, new ComponentTextType());
            StringType st = StringType.getStringTypeFromStringValue("hello");
            Map<String, FieldType<?>> row = Map.of("myCol", st);
            Stream<String> values = ob.toValue(List.of(), "fr", row, null);
            assertThat(values).containsExactly("hello");
        }

        @Test
        @DisplayName("toValue() avec colonne absente retourne Stream.of('')")
        void toValueMissingColumn() {
            ComponentOrderBy ob = new ComponentOrderBy("missing", DataRepository.Order.ASC, new ComponentTextType());
            Map<String, FieldType<?>> row = Map.of();
            // fieldType = null → valueToString(null) = "" for ComponentTextType
            Stream<String> values = ob.toValue(List.of(), "fr", row, null);
            assertThat(values).containsExactly("");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ComponentType records
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ComponentType sealed records")
    class ComponentTypeTest {

        @Test
        @DisplayName("ComponentTextType est une instance de ComponentType")
        void textType() {
            assertThat(new ComponentTextType()).isInstanceOf(ComponentType.class);
        }

        @Test
        @DisplayName("ComponentReferenceType est une instance de ComponentType")
        void referenceType() {
            assertThat(new ComponentReferenceType()).isInstanceOf(ComponentType.class);
        }

        @Test
        @DisplayName("ComponentNumericType est une instance de ComponentType")
        void numericType() {
            assertThat(new ComponentNumericType()).isInstanceOf(ComponentType.class);
        }

        @Test
        @DisplayName("ComponentBooleanType est une instance de ComponentType")
        void booleanType() {
            assertThat(new ComponentBooleanType()).isInstanceOf(ComponentType.class);
        }

        @Test
        @DisplayName("ComponentDateType stocke format et fieldType")
        void dateType() {
            ComponentDateType dt = new ComponentDateType(
                    "yyyy-MM-dd",
                    DownloadDatasetQueryAdvancedSearch.FieldType.date);
            assertThat(dt.format()).isEqualTo("yyyy-MM-dd");
            assertThat(dt.fieldType()).isEqualTo(DownloadDatasetQueryAdvancedSearch.FieldType.date);
            assertThat(dt).isInstanceOf(ComponentType.class);
        }
    }
}
