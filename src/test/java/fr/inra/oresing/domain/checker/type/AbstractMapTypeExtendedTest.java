package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour {@link AbstractMapType} et ses sous-classes concrètes
 * {@link MapType} et {@link PatternType} – branches serialize non couvertes.
 */
@Tag("domain.checker")
@DisplayName("AbstractMapType – serialize variantes (gen+key, ObjectNode, ArrayNode)")
class AbstractMapTypeExtendedTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────
    // serialize(JsonGenerator, String) — tous les types de valeurs
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize(gen,key) avec valeur Integer")
    void serializeGenKeyInteger() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("n", 42);
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("42");
    }

    @Test
    @DisplayName("serialize(gen,key) avec valeur Float")
    void serializeGenKeyFloat() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("f", 3.14f);
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("3.14");
    }

    @Test
    @DisplayName("serialize(gen,key) avec valeur Boolean true")
    void serializeGenKeyBooleanTrue() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("b", true);
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("true");
    }

    @Test
    @DisplayName("serialize(gen,key) avec valeur null → NullNode")
    void serializeGenKeyNullValue() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("k", null);
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("null");
    }

    @Test
    @DisplayName("serialize(gen,key) avec IntegerType")
    void serializeGenKeyIntegerType() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("it", IntegerType.of(7));
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("7");
    }

    @Test
    @DisplayName("serialize(gen,key) avec FloatType")
    void serializeGenKeyFloatType() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("ft", FloatType.of(2.5f));
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("2.5");
    }

    @Test
    @DisplayName("serialize(gen,key) avec BooleanType")
    void serializeGenKeyBooleanType() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("bt", BooleanType.of(false));
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("false");
    }

    @Test
    @DisplayName("serialize(gen,key) avec NullType → NullNode")
    void serializeGenKeyNullType() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("nt", NullType.INSTANCE);
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("null");
    }

    @Test
    @DisplayName("serialize(gen,key) avec StringType générique → toString")
    void serializeGenKeyGenericFieldType() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("st", StringType.getStringTypeFromStringValue("hello"));
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("hello");
    }

    @Test
    @DisplayName("serialize(gen,key) avec valeur String (défaut) → toString")
    void serializeGenKeyDefaultString() throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("s", "plainString");
        MapType<String, Object> mt = new MapType<>(map);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
        gen.writeStartObject();
        mt.serialize(gen, "field");
        gen.writeEndObject();
        gen.close();

        assertThat(baos.toString()).contains("plainString");
    }

    // ─────────────────────────────────────────────────────────────────
    // serialize(ObjectNode, ObjectMapper, String)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("serialize(ObjectNode, mapper, key) avec valeur String")
    void serializeObjectNodeString() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "val");
        MapType<String, Object> mt = new MapType<>(map);

        ObjectNode node = MAPPER.createObjectNode();
        mt.serialize(node, MAPPER, "field");
        assertThat(node.has("field")).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────
    // serializeAddArray
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("serializeAddArray ajoute un ObjectNode dans l'ArrayNode")
    void serializeAddArray() {
        // serializeAddArray caste les valeurs en JsonNode — utiliser TextNode
        Map<String, Object> map = new HashMap<>();
        map.put("k", com.fasterxml.jackson.databind.node.TextNode.valueOf("v"));
        MapType<String, Object> mt = new MapType<>(map);

        ArrayNode arrayNode = MAPPER.createArrayNode();
        mt.serializeAddArray(arrayNode);
        assertThat(arrayNode.size()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────
    // MapType.copy() et serialize(JsonGenerator gen) → exception
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MapType.copy() retourne une nouvelle instance avec les mêmes entrées")
    void mapTypeCopy() {
        Map<String, Object> map = new HashMap<>();
        map.put("a", "b");
        MapType<String, Object> mt = new MapType<>(map);
        @SuppressWarnings("unchecked")
        FieldType<Map<String, Object>> copy = mt.copy();
        assertThat(copy).isNotSameAs(mt);
        assertThat(copy.getValue()).containsKey("a");
    }

    @Test
    @DisplayName("MapType.copy() avec map null → map vide")
    void mapTypeCopyNull() {
        MapType<String, Object> mt = new MapType<>(null);
        @SuppressWarnings("unchecked")
        FieldType<Map<String, Object>> copy = mt.copy();
        assertThat(copy.getValue()).isEmpty();
    }

    @Test
    @DisplayName("MapType.serialize(gen) lève IllegalArgumentException")
    void mapTypeSerializeGenThrows() {
        MapType<String, Object> mt = new MapType<>(new HashMap<>());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> {
            JsonGenerator gen = new com.fasterxml.jackson.core.JsonFactory().createGenerator(baos);
            mt.serialize(gen);
            gen.close();
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MapType.getSqlType() retourne JSONB")
    void mapTypeGetSqlType() {
        MapType<String, Object> mt = new MapType<>(new HashMap<>());
        assertThat(mt.getSqlType()).isEqualTo(SqlPrimitiveType.JSONB);
    }

    @Test
    @DisplayName("MapType.check() retourne null")
    void mapTypeCheckNull() {
        MapType<String, Object> mt = new MapType<>(new HashMap<>());
        assertThat(mt.check("anything", null)).isNull();
    }

    @Test
    @DisplayName("MapType.toJsonForDatabase() retourne this")
    void mapTypeToJsonForDatabase() {
        MapType<String, Object> mt = new MapType<>(new HashMap<>());
        assertThat(mt.toJsonForDatabase()).isSameAs(mt);
    }
}
