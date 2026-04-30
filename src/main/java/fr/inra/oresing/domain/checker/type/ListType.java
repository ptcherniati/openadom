package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.SomethingToBeSentToFrontend;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultManyValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import lombok.Getter;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public non-sealed class ListType<F extends FieldType<?>> implements FieldType<List<F>> {

    /**
     * shared ObjectMapper. Each serialize() / serializeAddArray()
     * previously instantiated a new mapper per call ; on a 274 706-line
     * import that is millions of throwaway ObjectMappers. ObjectMapper is
     * thread-safe once configured.
     */
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    public static final ListType<StringType> EMPTY_LIST = new ListType<>(StringType.getStringTypeFromStringValue(""));
    final Supplier<ListType<F>> clone;
    @Getter
    private final F fieldType;
    List<F> value = new LinkedList<>();

    public ListType(F fieldType) {
        this.fieldType = fieldType;
        clone = () -> new ListType(fieldType.copy());
    }

    public static ListType getListTypeFromListValue(final List<StringType> value) {
        final ListType listType = new ListType(StringType.getStringTypeFromStringValue(""));
        listType.value = value;
        return listType;
    }

    public static ListType<StringType> ofStringType() {
        return new ListType<>(new StringType(""));
    }

    @Override
    public List<F> getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        final FieldType<?> underlyingType = lineChecker.fieldTypeForOne();
        final List<ValidationCheckResult> collect = Arrays.stream(value.split(","))
                .map(v -> underlyingType.check(v, lineChecker))
                .map(v -> {
                    this.value.add((F) underlyingType.copy());
                    return v;
                })
                .collect(Collectors.toList());
        return new DefaultManyValidationCheckResult(collect, lineChecker.target());
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final ListType<F> listType = clone.get();
        if (value != null) {
            listType.value = value.stream().collect(Collectors.toCollection(ArrayList::new));
        }
        return listType;
    }

    @Override
    public String toString() {
        return value.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        final ArrayNode arrayNode = mapper.createArrayNode();
        for (final F ft : value) {
            ft.serializeAddArray(arrayNode);
        }
        node.set(key, arrayNode);
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        final ObjectMapper mapper = SHARED_MAPPER; // reused
        final ArrayNode arrayNode = mapper.createArrayNode();
        for (final F ft : value) {
            ft.serializeAddArray(arrayNode);
        }
        gen.writeObject(arrayNode);
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        final ObjectMapper mapper = SHARED_MAPPER; // reused
        final ArrayNode arrayNode = mapper.createArrayNode();
        for (final F ft : value) {
            ft.serializeAddArray(arrayNode);
        }
        gen.writeFieldName(key);
        mapper.writeValue(gen, arrayNode);
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        final ObjectMapper mapper = SHARED_MAPPER; // reused
        final ArrayNode an = mapper.createArrayNode();
        for (final F ft : value) {
            ft.serializeAddArray(an);
        }
        arrayNode.add(an);
    }

    @Override
    public Object toJsonForFrontend() {
        return value.stream()
                .map(SomethingToBeSentToFrontend::toJsonForFrontend)
                .toArray();

    }

    public void add(final F value) {
        getValue().add(value);
    }

    public void merge(final ListType<StringType> listType) {
        getValue().addAll((Collection<? extends F>) listType.getValue().stream().toList());
    }
}