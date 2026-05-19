package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public sealed interface FieldType<T> extends SomethingToBeStoredAsJsonInDatabase, SomethingToBeSentToFrontend
        permits
         ReferenceType,
         BooleanType,
         DateType,
         FloatType,
         IntegerType,
         ListType,
         MapType,
         PatternType,
         AbstractMapType,
         NullType,
         StringType {


    public static <T> fr.inra.oresing.domain.checker.type.FieldType<T> readObject(final T fieldType) {
        return (fr.inra.oresing.domain.checker.type.FieldType<T>) switch (fieldType) {
            case null -> NullType.INSTANCE;
            case List list -> {
                final List<FieldType<?>> collect = list.stream()
                        .map(FieldType::readObject)
                        .toList();
                final FieldType<?> innerFieldType = !collect.isEmpty() ? collect.getFirst() : StringType.getStringTypeFromStringValue("");
                ListType<FieldType<?>> listType = new ListType<>(innerFieldType);
                listType.value = collect;
                yield listType;
            }
            case Integer integer -> IntegerType.of(integer);
            case Double d -> FloatType.of(d.floatValue());
            case Boolean b -> BooleanType.of(b);
            case Map map -> {
                Map<String, fr.inra.oresing.domain.checker.type.FieldType<?>> mapOfFieldTypes = new HashMap<>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                    String key = entry.getKey();
                    fr.inra.oresing.domain.checker.type.FieldType<?> fieldType1 = readObject(entry.getValue());
                    mapOfFieldTypes.put(key, fieldType1);
                }
                yield new MapType(mapOfFieldTypes);
            }
            case String s when Pattern.compile(DateType.PATTERN_DATE_REGEXP).matcher(s).matches() ->
                    DateType.of(fieldType.toString());
            case String s -> StringType.getStringTypeFromStringValue(s);
            default -> StringType.getStringTypeFromStringValue(fieldType.toString());
        };
    }

    T getValue();

    SqlPrimitiveType getSqlType();

    CheckerValidationCheckResult check(String value, LineChecker lineChecker);

    FieldType<T> copy();

    void serialize(JsonGenerator gen) throws IOException;

    default DataColumnValue transform(final LineChecker lineChecker,
                                      final DataColumnValue referenceColumnRawValue,
                                      final DataColumn referenceColumn,
                                      final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedToBuilder) {
        return referenceColumnRawValue;
    }

    void serialize(JsonGenerator gen, String key) throws IOException;

    void serialize(ObjectNode rootNode, ObjectMapper mapper, String key);

    void serializeAddArray(ArrayNode arrayNode);

    default String toStringForComponentValue() {
        return toString();
    }

    default CheckerValidationCheckResult postTreatment(CheckerValidationCheckResult checkerValidationCheckResult) {
        return checkerValidationCheckResult;
    }
}