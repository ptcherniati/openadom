package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;
import java.util.Set;

@Value
public class GetReferenceResult {
    Set<ReferenceValue> referenceValues;

    @Value
    public static class ReferenceValue {
        String hierarchicalKey;
        String hierarchicalReference;
        String naturalKey;
        Map<String, String> values;
    }
}