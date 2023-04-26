package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class GetReferenceResult {
    Set<ReferenceValue> referenceValues;
    Map<String, String> referenceTypeForReferencingColumns;

    @Value
    public static class ReferenceValue {
        String id;
        String hierarchicalKey;
        String hierarchicalReference;
        String naturalKey;
        Map<String, Object> values;
        Map<String, Set<UUID>> refsLinkedTo;
    }
}