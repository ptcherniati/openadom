package fr.inra.oresing.domain.checker.type;

import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract sealed class AbstractType<T> implements FieldType<T> permits ReferenceType {
    protected CheckerTarget target;
    protected LineChecker.Transformer transformer;

    public static <T> FieldType<T> readObject(final T fieldType) {
        return (FieldType<T>) switch (fieldType) {
            case null -> NullType.INSTANCE;
            case List list -> {
                List<FieldType<?>> collect = (List<FieldType<?>>) list.stream()
                        .map(AbstractType::readObject)
                        .toList();
                FieldType<?> innerFieldType = !collect.isEmpty() ? collect.getFirst() : StringType.getStringTypeFromStringValue("");
                ListType listType = new ListType<>(innerFieldType);
                listType.value = collect;
                yield listType;
            }
            case Integer integer -> IntegerType.of(integer);
            case Double d -> FloatType.of(d.floatValue());
            case Boolean b -> BooleanType.of(b);
            case Map map -> {
                Map<String, FieldType<?>> mapOfFieldTypes = new HashMap<>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                    String key = entry.getKey();
                    FieldType<?> fieldType1 = readObject(entry.getValue());
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
}