package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import fr.inra.oresing.domain.data.SomethingToBeSentToFrontend;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract non-sealed class AbstractMapType<K, V> implements FieldType<Map<K, V>> {
    private final Map<K, V> value;

    protected AbstractMapType(Map<K, V> value) {
        this.value = value != null ? value : new HashMap<>();
    }

    @Override
    public Map<K, V> getValue() {
        return value;
    }

    @Override
    public Object toJsonForFrontend() {
        final Map<K, Object> returnMap = new HashMap<>();
        for (final Map.Entry<K, V> kvEntry : value.entrySet()) {
            final V value1 = kvEntry.getValue();
            final K key = kvEntry.getKey();
            if (value1 instanceof SomethingToBeSentToFrontend) {
                final Object jsonForFrontend = ((SomethingToBeSentToFrontend<?>) value1).toJsonForFrontend();
                returnMap.put(key, jsonForFrontend);
            } else {
                returnMap.put(key, value1);
            }
        }
        return returnMap;
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode mapNode = mapper.createObjectNode();
        for (final Map.Entry<K, V> kvEntry : value.entrySet()) {
            switch (kvEntry.getValue()) {
                case null -> mapNode.set((String) kvEntry.getKey(), NullNode.getInstance());
                case Integer integer -> mapNode.put((String) kvEntry.getKey(), integer);
                case IntegerType integerType -> mapNode.put((String) kvEntry.getKey(), integerType.getValue());
                case Float floating -> mapNode.put((String) kvEntry.getKey(), floating);
                case FloatType floatType -> mapNode.put((String) kvEntry.getKey(), floatType.getValue());
                case Boolean bool -> mapNode.put((String) kvEntry.getKey(), bool);
                case BooleanType booleanType -> mapNode.put((String) kvEntry.getKey(), booleanType.getValue());
                case NullType ignored -> mapNode.set((String) kvEntry.getKey(), NullNode.getInstance());
                case FieldType<?> fieldType -> mapNode.put((String) kvEntry.getKey(), fieldType.toString());
                default -> mapNode.put((String) kvEntry.getKey(), kvEntry.getValue().toString());
            }
        }
        gen.writeFieldName(key);
        mapper.writeValue(gen, mapNode);
    }

    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        final ObjectNode mapNode = mapper.createObjectNode();
        for (final Map.Entry<K, V> kvEntry : value.entrySet()) {
            mapNode.set((String) kvEntry.getKey(), kvEntry.getValue() instanceof JsonNode ? (JsonNode) kvEntry.getValue() : new TextNode(kvEntry.getValue().toString()));
        }
        node.set(key, mapNode);
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode mapNode = mapper.createObjectNode();
        for (final Map.Entry<K, V> kvEntry : value.entrySet()) {
            mapNode.set((String) kvEntry.getKey(), (JsonNode) kvEntry.getValue());
        }
        arrayNode.add(mapNode);
    }
}