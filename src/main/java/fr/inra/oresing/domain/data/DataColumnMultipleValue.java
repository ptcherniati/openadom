package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.ManyValuesStaticColumn;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne est multi-valuées ({@link Multiplicity#MANY}).
 *
 * @param <U>
 */
@Value
public class DataColumnMultipleValue<U> implements DataColumnValue<ListType<FieldType<?>>, FieldType<?>> {

    private static final String COLLECTION_AS_JSON_STRING_SEPARATOR = ",";
    ListType<FieldType<?>> values;

    @SuppressWarnings("unchecked")
    public DataColumnMultipleValue(final List<?> values) {
        super();
        final ListType<FieldType<?>> fieldsType = new ListType<>(StringType.getStringTypeFromStringValue(null));
        for (final Object value : values) {
            if (value instanceof FieldType) {
                fieldsType.getValue().add((FieldType<?>) value);
            } else {
                fieldsType.getValue().add(StringType.getStringTypeFromStringValue("" + value));
            }
        }
        this.values = fieldsType;
    }

    public DataColumnMultipleValue(final ListType<FieldType<?>> values) {
        this.values = values;
    }

    @Override
    public ListType<FieldType<?>> toJsonForDatabase() {
        return values;
    }

    @Override
    public FieldType<?> getValuesToCheck() {

        return values;
    }

    @Override
    public DataColumnValue<ListType<FieldType<?>>, FieldType<?>> transform(UnaryOperator<FieldType<?>> transformation) {
        final ListType<FieldType<?>> fieldType = Optional.ofNullable(values)
                .map(transformation)
                .filter(ListType.class::isInstance)
                .map(t -> (ListType<FieldType<?>>) t)
                .orElse(values);
        return Optional.ofNullable(fieldType)
                .map(ListType::getValue)
                .map(v -> new DataColumnMultipleValue<U>(v))
                .orElse(new DataColumnMultipleValue<>(List.of()));
    }

    private U stringToValue(final String s) {
        U u = (U) values;
        return (U) s;
    }

    @Override
    public String toValueString(final AsynchroneFileImporterContext referenceImporterContext, final String referencedColumn, final String locale) {
        return values.getValue().stream()
                .map(s -> referenceImporterContext.getDisplayNamesByReferenceAndNaturalKey(referencedColumn, s.toString(), locale))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    @SuppressWarnings("java:S1452")
    public FieldType<?> toJsonForFrontend() {
        return values.copy();
    }

    public String getCsvCellContent() {
        List<String> cellValues = values.getValue().stream()
                .map(Object::toString)
                .toList();
        for (String value : cellValues) {
            if (value.contains(ManyValuesStaticColumn.CSV_CELL_SEPARATOR)) {
                throw new IllegalStateException(
                        String.format(ExceptionMessage.SEPARATOR_USING_IN_VALUE.toMessage(), value, ManyValuesStaticColumn.CSV_CELL_SEPARATOR)
                );
            }
        }
        return String.join(ManyValuesStaticColumn.CSV_CELL_SEPARATOR, cellValues);
    }
}