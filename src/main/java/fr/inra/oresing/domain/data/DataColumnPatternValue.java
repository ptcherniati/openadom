package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record DataColumnPatternValue(
        Map<DataColumn, DataColumnValue> values) implements DataColumnValue<Map<String, Object>, Map<String, Object>> {

    public DataColumnPatternValue(FieldType valuesToCheck) {
        this(switch (valuesToCheck) {
            case PatternType patternType -> patternType.getValue();
            case null, default -> new HashMap<>();
        });
    }

    @Override
    public PatternType getValuesToCheck() {
        Map<String, FieldType> valuesToCheck = values().entrySet()
                .stream().collect(Collectors.toMap(e -> e.getKey().column(), e -> e.getValue().getValuesToCheck()));
        return new PatternType<>(valuesToCheck);
    }

    @Override
    public DataColumnPatternValue transform(final Function<FieldType, FieldType> transformation) {
        final Map<Ltree, String> transformedValues = null;//Maps.transformValues(values, transformation::apply);
        return new DataColumnPatternValue((FieldType) null);
    }

    @Override
    public String toValueString(final DataImporterContext referenceImporterContext, final String referencedColumn, final String locale) {
        return values.entrySet().stream()
                .map(ltreeStringEntry -> String.format("\"%s\"\"=%s\"", referenceImporterContext.getDisplayNamesByReferenceAndNaturalKey(referencedColumn, ltreeStringEntry.getKey().toString(), locale), ltreeStringEntry.getValue()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public Map<String, Object> toJsonForFrontend() {
        return toStringStringMap();
    }

    @Override
    public Map<String, Object> toJsonForDatabase() {
        return toStringStringMap();
    }

    private Map<String, Object> toStringStringMap() {
        return values.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().column(), entry -> {
                    Object value = entry.getValue().toJsonForDatabase();
                    return switch (value) {
                        case IntegerType integerType -> integerType.getValue();
                        case BooleanType booleanType -> booleanType.getValue();
                        case FloatType floatType -> floatType.getValue();
                        case NullType nullType -> Optional.empty();
                        default -> value;
                    };
                }));
    }

    public void put(DataColumn secondPatternOfColumn, DataColumnValue valueToStoreInDatabase) {
        values().put(secondPatternOfColumn, valueToStoreInDatabase);
    }
}