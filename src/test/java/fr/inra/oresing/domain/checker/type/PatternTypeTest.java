package fr.inra.oresing.domain.checker.type;

import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour {@link PatternType} – méthodes non couvertes selon Sonar.
 */
@Tag("domain.checker")
@DisplayName("PatternType – copy, toStringForComponentValue, getColumnValue, postTreatment")
class PatternTypeTest {

    @Test
    @DisplayName("copy() retourne une nouvelle instance")
    void copy() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put(Column.__VALUE__, StringType.getStringTypeFromStringValue("hello"));
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);

        @SuppressWarnings("unchecked")
        PatternType<String, FieldType<?>> copy = (PatternType<String, FieldType<?>>) pt.copy();
        assertThat(copy).isNotSameAs(pt);
        assertThat(copy).isNotNull();
    }

    @Test
    @DisplayName("toStringForComponentValue() retourne la valeur de __VALUE__")
    void toStringForComponentValueWithValue() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put(Column.__VALUE__, StringType.getStringTypeFromStringValue("testVal"));
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);
        assertThat(pt.toStringForComponentValue()).isEqualTo("testVal");
    }

    @Test
    @DisplayName("toStringForComponentValue() retourne chaîne vide si __VALUE__ null")
    void toStringForComponentValueNull() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put(Column.__VALUE__, null);
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);
        assertThat(pt.toStringForComponentValue()).isEmpty();
    }

    @Test
    @DisplayName("getColumnValue() retourne le FieldType de __VALUE__")
    void getColumnValue() {
        Map<String, FieldType<?>> map = new HashMap<>();
        StringType st = StringType.getStringTypeFromStringValue("data");
        map.put(Column.__VALUE__, st);
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);
        FieldType<?> colVal = pt.getColumnValue();
        assertThat(colVal).isSameAs(st);
    }

    @Test
    @DisplayName("getColumnValue() retourne NullType si __VALUE__ absent")
    void getColumnValueNotPresent() {
        PatternType<String, FieldType<?>> pt = new PatternType<>(new HashMap<>());
        FieldType<?> colVal = pt.getColumnValue();
        assertThat(colVal).isSameAs(NullType.INSTANCE);
    }

    @Test
    @DisplayName("getColumnValue() retourne NullType si map null")
    void getColumnValueNullMap() {
        PatternType<String, FieldType<?>> pt = new PatternType<>(null);
        FieldType<?> colVal = pt.getColumnValue();
        assertThat(colVal).isSameAs(NullType.INSTANCE);
    }

    @Test
    @DisplayName("postTreatment() retourne un PatternValidationCheckResult")
    void postTreatment() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put(Column.__VALUE__, StringType.getStringTypeFromStringValue("x"));
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);

        fr.inra.oresing.domain.checker.CheckerTarget target =
                new fr.inra.oresing.domain.data.DataColumn("col");
        CheckerValidationCheckResult<FieldType<?>> baseResult =
                fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultCheckerValidationCheckResult
                        .success(target, StringType.getStringTypeFromStringValue("x"));

        CheckerValidationCheckResult<?> result = pt.postTreatment(baseResult);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("serialize(gen) lève IllegalArgumentException")
    void serializeGenThrows() {
        PatternType<String, FieldType<?>> pt = new PatternType<>(new HashMap<>());
        assertThatThrownBy(() -> pt.serialize((com.fasterxml.jackson.core.JsonGenerator) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getSqlType() retourne JSONB")
    void getSqlType() {
        PatternType<String, FieldType<?>> pt = new PatternType<>(new HashMap<>());
        assertThat(pt.getSqlType()).isEqualTo(SqlPrimitiveType.JSONB);
    }

    @Test
    @DisplayName("check() retourne null")
    void checkNull() {
        PatternType<String, FieldType<?>> pt = new PatternType<>(new HashMap<>());
        assertThat(pt.check("anything", null)).isNull();
    }
}
