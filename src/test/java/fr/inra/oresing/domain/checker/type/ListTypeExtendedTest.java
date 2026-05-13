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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires étendus pour {@link ListType} – serialize variantes, merge, toString.
 */
@Tag("domain.model")
@DisplayName("ListType – serialize, merge, toString, check")
class ListTypeExtendedTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("toString() retourne les valeurs séparées par virgules")
    void toStringJoined() {
        ListType<StringType> lt = ListType.getListTypeFromListValue(List.of(
                StringType.getStringTypeFromStringValue("a"),
                StringType.getStringTypeFromStringValue("b")));
        assertThat(lt.toString()).isEqualTo("a,b");
    }

    @Test
    @DisplayName("merge() ajoute les éléments d'un autre ListType")
    void merge() {
        ListType<StringType> lt = ListType.ofStringType();
        lt.add(StringType.getStringTypeFromStringValue("x"));
        ListType<StringType> other = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("y"),
                        StringType.getStringTypeFromStringValue("z")));
        lt.merge(other);
        assertThat(lt.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("serialize(ObjectNode, mapper, key) insère un ArrayNode")
    void serializeObjectNode() {
        ListType<StringType> lt = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("one")));
        ObjectNode node = MAPPER.createObjectNode();
        lt.serialize(node, MAPPER, "items");
        assertThat(node.has("items")).isTrue();
        assertThat(node.get("items").isArray()).isTrue();
        assertThat(node.get("items").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("serialize(JsonGenerator) écrit un tableau JSON")
    void serializeGenerator() throws IOException {
        ListType<StringType> lt = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("hello")));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Use ObjectMapper's generator to have an ObjectCodec
        JsonGenerator gen = MAPPER.getFactory().createGenerator(baos);
        gen.setCodec(MAPPER);
        lt.serialize(gen);
        gen.close();
        assertThat(baos.toString()).contains("hello");
    }

    @Test
    @DisplayName("serialize(JsonGenerator, key) écrit un tableau JSON avec clé")
    void serializeGeneratorWithKey() throws IOException {
        ListType<StringType> lt = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("item1")));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = MAPPER.getFactory().createGenerator(baos);
        gen.setCodec(MAPPER);
        gen.writeStartObject();
        lt.serialize(gen, "myList");
        gen.writeEndObject();
        gen.close();
        assertThat(baos.toString()).contains("myList");
        assertThat(baos.toString()).contains("item1");
    }

    @Test
    @DisplayName("serializeAddArray() ajoute un ArrayNode dans l'ArrayNode parent")
    void serializeAddArray() {
        ListType<StringType> lt = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("nested")));
        ArrayNode parent = MAPPER.createArrayNode();
        lt.serializeAddArray(parent);
        assertThat(parent.size()).isEqualTo(1);
        assertThat(parent.get(0).isArray()).isTrue();
    }

    @Test
    @DisplayName("getSqlType() retourne TEXT")
    void getSqlType() {
        assertThat(ListType.ofStringType().getSqlType()).isEqualTo(SqlPrimitiveType.TEXT);
    }

    @Test
    @DisplayName("toJsonForDatabase() retourne this")
    void toJsonForDatabase() {
        ListType<StringType> lt = ListType.ofStringType();
        assertThat(lt.toJsonForDatabase()).isSameAs(lt);
    }

    @Test
    @DisplayName("getFieldType() retourne le type des éléments")
    void getFieldType() {
        ListType<StringType> lt = ListType.ofStringType();
        assertThat(lt.getFieldType()).isNotNull();
    }
}
