package fr.inra.oresing.domain.data;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.ManyValuesStaticColumn;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne est multi-valuées ({@link Multiplicity#MANY}).
 *
 * @param <U>
 */
@Value
public class DataColumnMultipleValue<U> implements DataColumnValue<ListType, FieldType> {

    private static final String COLLECTION_AS_JSON_STRING_SEPARATOR = ",";

    public DataColumnMultipleValue(final List values) {
        super();
        final ListType fieldsType = new ListType(StringType.getStringTypeFromStringValue(null));
        for (final Object value : values) {
            if (value instanceof FieldType) {
                fieldsType.getValue().add(value);
            } else {
                fieldsType.getValue().add(StringType.getStringTypeFromStringValue("" + value));
            }
        }
        this.values = fieldsType;
    }

    public DataColumnMultipleValue(final ListType values) {
        this.values = values;
    }

    ListType values;

    @Override
    public ListType toJsonForDatabase() {
        return values;
    }

    @Override
    public FieldType getValuesToCheck() {

        return values;
    }

    @Override
    public DataColumnMultipleValue transform(final Function<FieldType, FieldType> transformation) {
        final ListType fieldType = (ListType) Optional.ofNullable(values)
                .map(transformation)
                .orElse(values);
        return new DataColumnMultipleValue(fieldType.getValue());
    }

    private U stringToValue(final String s) {
        U u = (U) values;
        return (U) s;
    }

    @Override
    public String toValueString(final DataImporterContext referenceImporterContext, final String referencedColumn, final String locale) {
        return (String) values.getValue().stream()
                .map(s -> referenceImporterContext.getDisplayNamesByReferenceAndNaturalKey(referencedColumn, s.toString(), locale))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public FieldType toJsonForFrontend() {
        return values.copy();
    }

    public String getCsvCellContent() {
        return (String) values.getValue().stream()
                .map(Object::toString)
                .peek(value -> Preconditions.checkState(
                                !value.toString().contains(ManyValuesStaticColumn.CSV_CELL_SEPARATOR),
                                ExceptionMessage.SEPARATOR_USING_IN_VALUE.toMessage(),
                                value,
                                ManyValuesStaticColumn.CSV_CELL_SEPARATOR
                        )
                )
                .collect(Collectors.joining(ManyValuesStaticColumn.CSV_CELL_SEPARATOR));

    }
}