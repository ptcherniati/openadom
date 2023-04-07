package fr.inra.oresing.rest;

import fr.inra.oresing.model.internationalization.Internationalization;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class GetReferenceResult {
    Set<ReferenceValue> referenceValues;

    @Value
    public static class ReferenceValue {
        String hierarchicalKey;
        String hierarchicalReference;
        String naturalKey;
        Map<String, Object> values;
        Map<String, Set<UUID>> refsLinkedTo;
    }
}