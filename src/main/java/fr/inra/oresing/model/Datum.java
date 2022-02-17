package fr.inra.oresing.model;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.LinkedHashMap;
import java.util.Map;

public class Datum implements SomethingThatCanProvideEvaluationContext{

    private final Map<VariableComponentKey, String> values;

    public Datum() {
        this(new LinkedHashMap<>());
    }

    public Datum(Map<VariableComponentKey, String> values) {
        this.values = values;
    }

    public static Datum copyOf(Datum datum) {
        return new Datum(new LinkedHashMap<>(datum.asMap()));
    }

    public static Datum fromMapMap(Map<String, Map<String, String>> line) {
        Map<VariableComponentKey, String> valuesPerReference = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> variableEntry : line.entrySet()) {
            String variable = variableEntry.getKey();
            for (Map.Entry<String, String> componentEntry : variableEntry.getValue().entrySet()) {
                String component = componentEntry.getKey();
                VariableComponentKey reference = new VariableComponentKey(variable, component);
                valuesPerReference.put(reference, componentEntry.getValue());
            }
        }
        return new Datum(ImmutableMap.copyOf(valuesPerReference));
    }

    public String get(VariableComponentKey variableComponentKey) {
        return values.get(variableComponentKey);
    }

    public Map<VariableComponentKey, String> asMap() {
        return values;
    }

    public Map<String, Map<String, String>> asMapMap() {
        final Map<String, Map<String, String>> datumAsMapMap = new LinkedHashMap<>();
        for (Map.Entry<VariableComponentKey, String> entry2 : asMap().entrySet()) {
            String variable = entry2.getKey().getVariable();
            String component = entry2.getKey().getComponent();
            String value = entry2.getValue();
            datumAsMapMap.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
        }
        return datumAsMapMap;
    }

    public Datum filterOnVariable(Predicate<VariableComponentKey> includeInDataGroupPredicate) {
        Map<VariableComponentKey, String> filteredValues = Maps.filterKeys(values, includeInDataGroupPredicate);
        return new Datum(filteredValues);
    }

    public String put(VariableComponentKey variableComponentKey, String value) {
        return values.put(variableComponentKey, value);
    }

    public void putAll(Datum rowWithValues) {
        values.putAll(rowWithValues.values);
    }

    @Override
    public ImmutableMap<String, Object> getEvaluationContext() {
        return ImmutableMap.of("datum", asMapMap(), "datumByVariableAndComponent", asMapMap());
    }
}
