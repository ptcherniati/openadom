package fr.inra.oresing.domain.data;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;

import java.util.*;
import java.util.stream.Collectors;

public class DataDatum implements SomethingThatCanProvideEvaluationContext, SomethingToBeStoredAsJsonInDatabase<Map<String, FieldType>>, SomethingToBeSentToFrontend<Map<String, FieldType>> {

    private final Map<DataColumn, DataColumnValue> values;

    public DataDatum() {
        this(new LinkedHashMap<>());
    }

    public DataDatum(final Map<DataColumn, DataColumnValue> values) {
        super();
        this.values = values;
    }

    public static DataDatum copyOf(final DataDatum referenceDatum) {
        return new DataDatum(new LinkedHashMap<>(referenceDatum.values));
    }

    public static DataDatum fromDatabaseJson(final Map<String, Object> mapFromDatabase) {
        final Map<DataColumn, DataColumnValue> result = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : mapFromDatabase.entrySet()) {
            final DataColumn referenceColumn = new DataColumn(entry.getKey());
            final Object storedValue = entry.getValue();
            final DataColumnValue referenceColumnValue;
            switch (storedValue) {
                case final Map map -> {
                    final Map<String, String> castedStoredValue = (Map<String, String>) map;
                    final Map<Ltree, String> storedValueAs = castedStoredValue.entrySet().stream()
                            .collect(Collectors.toMap(
                                    storedValueEntry -> Ltree.fromSql(storedValueEntry.getKey()),
                                    Map.Entry::getValue));
                    referenceColumnValue = new DataColumnIndexedValue(storedValueAs);
                }
                case final Collection collection -> {
                    final List<FieldType> fieldTypes = ((Collection<Object>) storedValue).stream()
                            .map(AbstractType::readObject)
                            .collect(Collectors.toList());
                    referenceColumnValue = new DataColumnMultipleValue(fieldTypes);

                }
                case null, default ->
                        referenceColumnValue = new DataColumnSingleValue(AbstractType.readObject(storedValue));
            }
            result.put(referenceColumn, referenceColumnValue);
        }
        return new DataDatum(result);
    }

    public boolean contains(final DataColumn column) {
        return values.containsKey(column) ||
               values().entrySet()
                       .stream()
                       .filter(entry -> entry.getValue() instanceof DataColumnPatternValue)
                       .flatMap(entry -> ((DataColumnPatternValue) entry.getValue()).values().keySet().stream()
                               .map(registerColumn -> Column.__VALUE__.equals(registerColumn.column()) ? entry.getKey().column() : Column.COLUMN_IN_COLUMN_PATTERN.formatted(entry.getKey().column(), registerColumn.column()))
                       )
                       .map(DataColumn::new)
                       .anyMatch(registerColumn -> registerColumn.equals(column));
    }

    public DataColumnValue get(final DataColumn column) {
        Preconditions.checkArgument(
                contains(column),
                ExceptionMessage.MISSING_COLUMN.toMessage(),
                column.column(),
                values.keySet().stream().map(DataColumn::column).collect(Collectors.joining(" - "))
        );
        return Optional.of(values)
                .map(values -> values.get(column))
                .orElseGet(() -> values().entrySet().stream()
                        .filter(entry -> entry.getValue() instanceof DataColumnPatternValue)
                        .flatMap(entry -> ((DataColumnPatternValue) entry.getValue()).values().entrySet().stream()
                                .filter(storedColumn -> Column.COLUMN_IN_COLUMN_PATTERN.formatted(entry.getKey().column(), storedColumn.getKey().column()).equals(column.column())))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(Strings.lenientFormat(ExceptionMessage.MISSING_COLUMN.toMessage(), column.column(), values().values().stream()
                                .filter(DataColumnPatternValue.class::isInstance)
                                .map(DataColumnPatternValue.class::cast)
                                .map(DataColumnPatternValue::values)
                                .map(Map::keySet)
                                .flatMap(values -> values.stream().map(DataColumn::column))
                                .collect(Collectors.joining(" - "))))));
    }

    @Override
    public ImmutableMap<String, FieldType> toJsonForDatabase() {
        final Map<String, FieldType> map = new LinkedHashMap<>();
        for (final Map.Entry<DataColumn, DataColumnValue> entry : values.entrySet()) {
            if (entry.getValue() instanceof DataColumnIndexedValue) {
                final FieldType valueThatMayBeNull = Optional.of(entry.getValue())
                        .map(SomethingToBeStoredAsJsonInDatabase<FieldType>::toJsonForDatabase)
                        .orElse(new MapType(new HashMap<>()));
                map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
            } else if (entry.getValue() instanceof DataColumnPatternValue patternValue) {
                final FieldType<Map<String, Object>> valueThatMayBeNull = Optional.of(patternValue)
                        .map(DataColumnPatternValue::toJsonForDatabase)
                        .map(MapType::new)
                        .orElse(new MapType<>(Map.of()));
                map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
            } else {
                final FieldType valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                        .map(SomethingToBeStoredAsJsonInDatabase<FieldType>::toJsonForDatabase)
                        .orElse(StringType.getStringTypeFromStringValue(""));
                map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
            }
        }
        return ImmutableMap.copyOf(map);
    }

    public ImmutableMap<String, Object> toObjectsExposedInGroovyContext() {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final Map.Entry<DataColumn, DataColumnValue> entry : values.entrySet()) {
            if (entry.getValue() instanceof DataColumnPatternValue patternValue) {
                map.put(entry.getKey().toJsonForDatabase(), patternValue.toObjectsExposedInGroovyContext());
            } else {
                final Object valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                        .map(SomethingToBeStoredAsJsonInDatabase::toJsonForDatabase)
                        .map(Object::toString)
                        .orElse(null);
                map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
            }
        }
        return ImmutableMap.copyOf(map);
    }

    public void put(final DataColumn column, final DataColumnValue value) {
        final DataColumnValue replaced;
        if (values().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof DataColumnPatternValue)
                .anyMatch(entry -> ((DataColumnPatternValue) entry.getValue()).values().containsKey(column))) {
            replaced = values().entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof DataColumnPatternValue)
                    .filter(entry -> ((DataColumnPatternValue) entry.getValue()).values().containsKey(column))
                    .peek(entry -> ((DataColumnPatternValue) entry.getValue()).values().put(column, value))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            final boolean consistent = replaced == null || ((DataColumnPatternValue) replaced).values().get(column).getClass().equals(value.getClass());
            Preconditions.checkState(consistent, "dans ce cas, on est en train de remplacer un champs avec une valeur qui a une autre multiplicité, c'est sûrement une erreur");
        } else {
            replaced = values.put(column, value);
            final boolean consistent = replaced == null || replaced.getClass().equals(value.getClass());
            Preconditions.checkState(consistent, "dans ce cas, on est en train de remplacer un champs avec une valeur qui a une autre multiplicité, c'est sûrement une erreur");

        }
    }

    public void putAll(final DataDatum anotherReferenceDatum) {
        values.putAll(anotherReferenceDatum.values);
    }

    @Override
    public ImmutableMap<String, Object> getEvaluationContext() {
        return ImmutableMap.of("datum", toObjectsExposedInGroovyContext());
    }

    /**
     * Étant donné une colonne, l'ensemble des valeurs qui doivent être subir transformation et computationChecker
     */
    public FieldType getValuesToCheck(final DataColumn column) {
        return get(column).getValuesToCheck();
    }

    @Override
    public Map<String, FieldType> toJsonForFrontend() {
        final Map<String, FieldType> map = new LinkedHashMap<>();
        for (final Map.Entry<DataColumn, DataColumnValue> entry : values.entrySet()) {
            if (entry.getValue() instanceof DataColumnIndexedValue(
                    Map<Ltree, String> values1
            ) && values1 instanceof final Map<Ltree, String> m) {
                final Map<String, FieldType> mapOfTypes = new HashMap<>();
                for (final Map.Entry<Ltree, String> entryForMap : m.entrySet()) {
                    mapOfTypes.put(entryForMap.getKey().getSql(), StringType.getStringTypeFromStringValue(entryForMap.getValue()));
                }
                final MapType<String, FieldType> field = new MapType<>(mapOfTypes);
                map.put(entry.getKey().toJsonForDatabase(), field);
                continue;
            }
            final FieldType valueThatMayBeNull = Optional.ofNullable(entry.getValue())
                    .map(DataColumnValue<FieldType, FieldType>::toJsonForFrontend)
                    .orElse(null);
            map.put(entry.getKey().toJsonForDatabase(), valueThatMayBeNull);
        }
        return ImmutableMap.copyOf(map);
    }

    public DataDatum filterHidden(final Set<String> hiddenComponents) {
        return new DataDatum(
                values.entrySet().stream()
                        .filter(entry -> !hiddenComponents.contains(entry.getKey().column()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    public Map<DataColumn, DataColumnValue> values() {
        return values;
    }

    public DataDatum with(final DataDatum dataDatum) {
        final DataDatum datum = new DataDatum();
        datum.values().putAll(values);
        datum.values().putAll(dataDatum.values);
        return datum;
    }
}