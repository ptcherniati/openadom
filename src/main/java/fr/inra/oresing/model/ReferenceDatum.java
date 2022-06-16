package fr.inra.oresing.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;

import java.util.*;
import java.util.stream.Collectors;

public class ReferenceDatum implements SomethingThatCanProvideEvaluationContext, SomethingToBeStoredAsJsonInDatabase<Map<String, Object>>, SomethingToBeSentToFrontend<Map<String, Object>> {

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
            } else if (storedValue instanceof Map) {
                Map<String, String> castedStoredValue = (Map<String, String>) storedValue;
                Map<Ltree, String> storedValueAs = castedStoredValue.entrySet().stream()
                        .collect(Collectors.toMap(storedValueEntry -> Ltree.fromSql(storedValueEntry.getKey()), Map.Entry::getValue));
                referenceColumnValue = new ReferenceColumnIndexedValue(storedValueAs);
            } else if (storedValue instanceof Collection) {
                Set<String> collect = new HashSet<>(((Collection<String>) storedValue));
                referenceColumnValue = new ReferenceColumnMultipleValue(collect);
            } else {
                throw new SiOreIllegalArgumentException(
                        "badStoreValueType",
                        Map.of(
                                "referenceDatumKey",entry.getKey(),
                                "storeValueType", storedValue.getClass().getSimpleName(),
                                "knownStoreValueType", Set.of("String","Map<String, String>", "Set<String>")
                        )
                );
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
        ReferenceColumnValue replaced = values.put(column, value);
        boolean consistent = replaced == null || replaced.getClass().equals(value.getClass());
        Preconditions.checkState(consistent, "dans ce cas, on est en train de remplacer un champs avec une valeur qui a une autre multiplicité, c'est sûrement une erreur");
        return replaced;
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
    public Collection<String> getValuesToCheck(ReferenceColumn column) {
        return get(column).getValuesToCheck();
    }

    @Override
    public Map<String, Object> toJsonForFrontend() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<ReferenceColumn, ReferenceColumnValue> entry : values.entrySet()) {
            Object valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                    .map(ReferenceColumnValue::toJsonForFrontend)
                    .orElse(null);
            map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
        }
        return ImmutableMap.copyOf(map);
    }
}