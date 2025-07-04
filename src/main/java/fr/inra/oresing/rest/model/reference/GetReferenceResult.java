package fr.inra.oresing.rest.model.reference;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.RefsLinkedToValue;
import lombok.Value;

import java.util.Map;
import java.util.Set;

public record GetReferenceResult(Set<ReferenceValue> referenceValues,
                                 Map<String, String> referenceTypeForReferencingColumns) {
    @Value
    public static class ReferenceValue {
        String id;
        String patternColumnName;
        String hierarchicalKey;
        String naturalKey;
        Map<String, FieldType<?>> values;
        Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo;
        Map referencingReference;

        public String commparingValue() {
            return "%s_%s".formatted(hierarchicalKey, patternColumnName);
        }

        @JsonGetter("values")
        @JsonRawValue
        public ObjectNode getValues() {
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectNode rootNode = mapper.createObjectNode();
            for (final Map.Entry<String, FieldType<?>> entry : values.entrySet()) {
                entry.getValue().serialize(rootNode, mapper, entry.getKey());
            }
            return rootNode;
        }
    }
}