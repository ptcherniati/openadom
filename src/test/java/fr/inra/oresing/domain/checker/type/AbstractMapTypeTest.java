package fr.inra.oresing.domain.checker.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour PatternType (sous-classe concrète de AbstractMapType).
 */
@Tag("domain.checker")
@DisplayName("AbstractMapType via PatternType – tests unitaires")
class AbstractMapTypeTest {

    @Test
    @DisplayName("getValue retourne la map passée au constructeur")
    void getValue() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put("key", StringType.getStringTypeFromStringValue("val"));
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);
        assertThat(pt.getValue()).containsKey("key");
    }

    @Test
    @DisplayName("getValue avec null → map vide")
    void getValueWithNull() {
        PatternType<String, FieldType<?>> pt = new PatternType<>(null);
        assertThat(pt.getValue()).isEmpty();
    }

    @Test
    @DisplayName("toJsonForFrontend retourne une map d'objets")
    void toJsonForFrontend() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put("k1", StringType.getStringTypeFromStringValue("hello"));
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);
        Object result = pt.toJsonForFrontend();
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resMap = (Map<String, Object>) result;
        assertThat(resMap).containsKey("k1");
    }

    @Test
    @DisplayName("toJsonForFrontend avec valeur non-SomethingToBeSentToFrontend")
    void toJsonForFrontendWithNonFrontendValue() {
        Map<String, String> map = new HashMap<>();
        map.put("raw", "rawValue");
        PatternType<String, String> pt = new PatternType<>(map);
        Object result = pt.toJsonForFrontend();
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resMap = (Map<String, Object>) result;
        assertThat(resMap.get("raw")).isEqualTo("rawValue");
    }

    @Test
    @DisplayName("toJsonForDatabase retourne la map")
    void toJsonForDatabase() {
        Map<String, FieldType<?>> map = new HashMap<>();
        map.put("k2", IntegerType.of(5));
        PatternType<String, FieldType<?>> pt = new PatternType<>(map);
        Object result = pt.toJsonForDatabase();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("MapType.getValue retourne la map")
    void mapTypeGetValue() {
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("field", "value");
        MapType<String, Object> mapType = new MapType<>(rawMap);
        assertThat(mapType.getValue()).containsKey("field");
    }
}