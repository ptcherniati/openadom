package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.SomethingToBeSentToFrontend;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultManyValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import lombok.Getter;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public non-sealed class ListType<FT extends FieldType> implements FieldType<List> {
    public static final ListType<? extends FieldType> EMPTY_LIST =  new ListType(StringType.getStringTypeFromStringValue(""));
    @Getter
    private final FT fieldType;
    List<FT> value = new LinkedList<>();
    final Supplier<ListType> clone;
    public <U extends ListType<FT>> ListType(final FT fieldType) {
        this.fieldType = fieldType;
        clone = () -> new ListType(fieldType.copy());
    }

    public static FieldType getListTypeFromListValue(final List<StringType> value) {
        final ListType listType = new ListType(StringType.getStringTypeFromStringValue(""));
        listType.value = value;
        return listType;
    }

    @Override
    public List<FT> getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }

/*
    @Override
    public ValidationCheckResult check(String value, LineCheckerWarper lineCheckerWarper) {
        FieldType underlyingType = lineCheckerWarper.getUnderlyingType();
        List<UUID> uuids = new LinkedList<>();
        List<ValidationCheckResult> collect = Arrays.stream(value.split(","))
                .map(v -> underlyingType.check(v, lineCheckerWarper))
                .peek(v -> this.value.add((FT) underlyingType.copy()))
                .peek(v -> {
                    if (v instanceof ReferenceValidationCheckResult rvcr && rvcr!=null){
                        uuids.addAll(rvcr.matchedReferenceId());
                    }

                })
                .collect(Collectors.toList());
        return new DefaultManyValidationCheckResult(collect, lineCheckerWarper.getTarget());
    }
*/

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        final FieldType underlyingType = lineChecker.fieldTypeForOne();
        final List<ValidationCheckResult> collect = Arrays.stream(value.split(","))
                .map(v -> underlyingType.check(v, lineChecker))
                .peek(v -> this.value.add((FT) underlyingType.copy()))
                .collect(Collectors.toList());
        return new DefaultManyValidationCheckResult(collect, lineChecker.target());
    }

    @Override
    public FieldType toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final ListType listType = clone.get();
        listType.value = value;
        return listType;
    }

/*
    public DataColumnValue transform(LineCheckerWarper lineChecker, DataColumnValue referenceColumnRawValue, DataColumn referenceColumn, SetMultimap<DataColumn, String> rawValueReplacedByKeys, ImmutableSetMultimap.Builder<String, Set<UUID>> refsLinkedToBuilder) {
        ListType<FT> copy = (ListType<FT>) copy();
        return referenceColumnRawValue.transform(fieldType1 -> copy);
    }
*/

    @Override
    public String toString() {
        return value.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        final ArrayNode arrayNode = mapper.createArrayNode();
        for (final FT ft : value) {
            ft.serializeAddArray(arrayNode);
        }
        node.set(key, arrayNode);
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode arrayNode = mapper.createArrayNode();
        for (final FT ft : value) {
            ft.serializeAddArray(arrayNode);
        }
        gen.writeObject(arrayNode);
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode arrayNode = mapper.createArrayNode();
        for (final FT ft : value) {
            ft.serializeAddArray(arrayNode);
        }
        gen.writeFieldName(key);
        mapper.writeValue(gen, arrayNode);
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode an = mapper.createArrayNode();
        for (final FT ft : value) {
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

    public static ListType<StringType> ofStringType() {
        return new ListType<>(new StringType(""));
    }

    public void add(final FT value) {
        getValue().add(value);
    }

    public void merge(final ListType<StringType> listType) {
        getValue().addAll((Collection<? extends FT>) listType.getValue().stream().toList());
    }
}
