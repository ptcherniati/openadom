package fr.inra.oresing.domain.data;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import fr.inra.oresing.domain.checker.type.FieldType;

import java.util.LinkedHashMap;
import java.util.Map;

public class Datum implements SomethingThatCanProvideEvaluationContext {

    private final Map<String, FieldType> values;

    public Datum() {
        this(new LinkedHashMap<>());
    }

    public Datum(final Map<String, FieldType> values) {
        super();
        this.values = values;
    }

    public static Datum copyOf(final Datum datum) {
        return new Datum(new LinkedHashMap<>(datum.asMap()));
    }

    public static Datum fromMapMapOfFieldType(final Map<String, Map<String, FieldType>> line) {
        final Map<String, FieldType> valuesPerReference = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<String, FieldType>> variableEntry : line.entrySet()) {
            final String variable = variableEntry.getKey();
            for (final Map.Entry<String, FieldType> componentEntry : variableEntry.getValue().entrySet()) {
                final String component = componentEntry.getKey();
                valuesPerReference.put(component, componentEntry.getValue());
            }
        }
        return new Datum(ImmutableMap.copyOf(valuesPerReference));
    }

    public FieldType get(final String componentKey) {
        return values.get(componentKey);
    }

    public Map<String, FieldType> asMap() {
        return values;
    }

    public Datum filterOnVariable(final Predicate<String> includeInDataGroupPredicate) {
        final Map<String, FieldType> filteredValues = Maps.filterKeys(values, includeInDataGroupPredicate);
        return new Datum(filteredValues);
    }

    public FieldType put(final String componentKey, final FieldType value) {
        return values.put(componentKey, value);
    }

    public void putAll(final Datum rowWithValues) {
        values.putAll(rowWithValues.values);
    }

    @Override
    public ImmutableMap<String, Object> getEvaluationContext() {
        return ImmutableMap.of("datum", asMap(), "datumByComponent", asMap());
    }
}
