package fr.inra.oresing.model;

import com.google.common.collect.ImmutableMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReferenceDatum implements SomethingThatCanProvideEvaluationContext {
    private final Map<ReferenceColumn, String> values;

    public ReferenceDatum() {
        this(new LinkedHashMap<>());
    }

    public ReferenceDatum(Map<ReferenceColumn, String> values) {
        this.values = values;
    }

    public static ReferenceDatum copyOf(ReferenceDatum referenceDatum) {
        return new ReferenceDatum(new LinkedHashMap<>(referenceDatum.values));
    }

    public String get(ReferenceColumn column) {
        return values.get(column);
    }

    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<ReferenceColumn, String> entry : values.entrySet()) {
            String valueThatMayBeNull = entry.getValue();
            map.put(entry.getKey().asString(), valueThatMayBeNull);
        }
        return map;
    }

    public String put(ReferenceColumn string, String value) {
        return values.put(string, value);
    }

    public void putAll(ReferenceDatum anotherReferenceDatum) {
        values.putAll(anotherReferenceDatum.values);
    }

    @Override
    public ImmutableMap<String, Object> getEvaluationContext() {
        return ImmutableMap.of("datum", asMap());
    }
}