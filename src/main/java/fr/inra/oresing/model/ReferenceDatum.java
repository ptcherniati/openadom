package fr.inra.oresing.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReferenceDatum implements SomethingThatCanProvideEvaluationContext, SomethingToBeStoredAsJsonInDatabase<Map<String, Object>>, SomethingToBeSentToFrontend<Map<String, String>> {

    private final Map<ReferenceColumn, ReferenceColumnValue> values;

    public ReferenceDatum() {
        this(new LinkedHashMap<>());
    }

    public ReferenceDatum(Map<ReferenceColumn, ReferenceColumnValue> values) {
        this.values = values;
    }

    public static ReferenceDatum copyOf(ReferenceDatum referenceDatum) {
        return new ReferenceDatum(new LinkedHashMap<>(referenceDatum.values));
    }

    public static ReferenceDatum fromDatabaseJson(Map<String, Object> mapFromDatabase) {
        Map<ReferenceColumn, ReferenceColumnValue> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : mapFromDatabase.entrySet()) {
            ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
            Object storedValue = entry.getValue();
            ReferenceColumnValue referenceColumnValue;
            if (storedValue instanceof String) {
                referenceColumnValue = new ReferenceColumnSingleValue((String) storedValue);
            } else if (storedValue instanceof Collection) {
                Set<String> collect = new HashSet<>(((Collection<String>) storedValue));
                referenceColumnValue = new ReferenceColumnMultipleValue(collect);
            } else {
                throw new IllegalStateException("valeur inattendue en base pour un référentiel à la clé " + entry.getKey() + " dans " + mapFromDatabase);
            }
            result.put(referenceColumn, referenceColumnValue);
        }
        return new ReferenceDatum(result);
    }

    public boolean contains(ReferenceColumn column) {
        return values.containsKey(column);
    }

    public ReferenceColumnValue get(ReferenceColumn column) {
        Preconditions.checkArgument(contains(column), "pas de colonne " + column + " dans " + values);
        return values.get(column);
    }

    @Override
    public ImmutableMap<String, Object> toJsonForDatabase() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<ReferenceColumn, ReferenceColumnValue> entry : values.entrySet()) {
            Object valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                    .map(SomethingToBeStoredAsJsonInDatabase::toJsonForDatabase)
                    .orElse(null);
            map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
        }
        return ImmutableMap.copyOf(map);
    }

    public ImmutableMap<String, Object> toObjectsExposedInGroovyContext() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<ReferenceColumn, ReferenceColumnValue> entry : values.entrySet()) {
            Object valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                    .map(SomethingToBeStoredAsJsonInDatabase::toJsonForDatabase)
                    .orElse(null);
            map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
        }
        return ImmutableMap.copyOf(map);
    }

    public ReferenceColumnValue put(ReferenceColumn column, ReferenceColumnValue value) {
        return values.put(column, value);
    }

    public void putAll(ReferenceDatum anotherReferenceDatum) {
        values.putAll(anotherReferenceDatum.values);
    }

    @Override
    public ImmutableMap<String, Object> getEvaluationContext() {
        return ImmutableMap.of("datum", toObjectsExposedInGroovyContext());
    }

    /**
     * Étant donné une colonne, l'ensemble des valeurs qui doivent être subir transformation et checker
     */
    public Set<String> getValuesToCheck(ReferenceColumn column) {
        return get(column).getValuesToCheck();
    }

    @Override
    public Map<String, String> toJsonForFrontend() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<ReferenceColumn, ReferenceColumnValue> entry : values.entrySet()) {
            String valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                    .map(ReferenceColumnValue::toJsonForFrontend)
                    .orElse(null);
            map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
        }
        return ImmutableMap.copyOf(map);
    }
}