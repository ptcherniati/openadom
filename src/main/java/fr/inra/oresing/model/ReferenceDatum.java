package fr.inra.oresing.model;

import com.google.common.collect.ImmutableMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReferenceDatum implements SomethingThatCanProvideEvaluationContext {

    private final Map<String, String> values;

    public ReferenceDatum() {
        this(new LinkedHashMap<>());
    }

    public ReferenceDatum(Map<String, String> values) {
        this.values = values;
    }

    public static ReferenceDatum copyOf(ReferenceDatum referenceDatum) {
        return new ReferenceDatum(new LinkedHashMap<>(referenceDatum.asMap()));
    }

    public String get(String column) {
        return values.get(column);
    }

    public Map<String, String> asMap() {
        return values;
    }

    public String put(String string, String value) {
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
