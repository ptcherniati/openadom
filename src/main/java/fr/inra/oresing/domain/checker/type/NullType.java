package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultCheckerValidationCheckResult;


import java.io.IOException;
import java.util.function.Supplier;

public non-sealed class NullType implements FieldType<Void> {
    public static final NullType INSTANCE = new NullType();
    final Supplier<NullType> clone;

    final Void value = null;

    private NullType() {
        super();
        clone = () -> INSTANCE;
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }

    @Override
    public CheckerValidationCheckResult<NullType> check(final String value, final LineChecker<?> lineChecker) {
        final CheckerTarget target = lineChecker.target();
        return DefaultCheckerValidationCheckResult.success(target, this);
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public NullType copy() {
        return this;
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        gen.writeNull();
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        gen.writeObjectField(key, value);
    }

    @Override
    public String toString() {
        return null;
    }


    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        node.put(key, value.toString());
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        arrayNode.add(value.toString());

    }

    @Override
    public Object toJsonForFrontend() {
        return value;
    }
}