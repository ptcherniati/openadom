package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.BooleanValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.groovy.GroovyExpression;
import fr.inra.oresing.persistence.SqlPrimitiveType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public non-sealed class BooleanType implements FieldType<Boolean> {
    final Supplier<BooleanType> clone;
    private final GroovyExpression expression;
    private final ImmutableMap<String, Object> context;
    Boolean value;


    public BooleanType(final String expression, final Map<String, Object> context) {
        super();
        this.expression = GroovyExpression.forExpression(expression);
        this.context = ImmutableMap.<String, Object>builder().putAll(context).build();
        clone = () -> new BooleanType(expression, context);
    }

    public BooleanType(final String expression) {
        this(expression, new HashMap<>());
    }

    public BooleanType(final Boolean value) {
        super();
        this.value = value;
        clone = () -> of(value);
        context = null;
        expression = null;

    }

    public static BooleanType forExpression(final String expression, final ImmutableMap<String, Object> context) {

        return new BooleanType(
                expression,
                context
        );
    }

    public static BooleanType of(final boolean value) {
        return new BooleanType(value);
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.BOOLEAN;
    }

    @Override
    public CheckerValidationCheckResult<BooleanType> check(final String value, final LineChecker lineCheckerWarper) {
        this.value = Boolean.parseBoolean(value);
        return BooleanValidationCheckResult.success(lineCheckerWarper.target(), this);
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }

    @Override
    public FieldType copy() {
        final BooleanType booleanType = clone.get();
        booleanType.value = value;
        return booleanType;
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeBoolean(value);
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        gen.writeObjectField(key, value);
    }

    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        node.put(key, value);
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        arrayNode.add((boolean) value);

    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public Object toJsonForFrontend() {
        return value;
    }
}