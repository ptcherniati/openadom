package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.IntegerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;


import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public non-sealed class IntegerType implements FieldType<Integer> {
    public static final String LOWER_THAN_MIN = "LOWER_THAN_MIN";
    public static final String HIGHER_THAN_MAX = "HIGHER_THAN_MAX";
    private final Integer min;
    private final Integer max;
    final Supplier<IntegerType> clone;

    public IntegerType(final Integer min, final Integer max) {
        super();
        this.min = min;
        this.max = max;
        clone = () -> new IntegerType(min, max);
    }

    Integer value;

    public static IntegerType of(final Integer value) {
        final IntegerType integerType = new IntegerType(null, null);
        integerType.value = value;
        return integerType;
    }

    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.INTEGER;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        IntegerValidationCheckResult validationCheckResult;
        final CheckerTarget target = lineChecker.target();
        try {
            this.value = Integer.parseInt(value);
            if (min != null && this.value.compareTo(min) < 0) {
                throw new IllegalArgumentException(LOWER_THAN_MIN);
            }
            if (max != null && this.value.compareTo(max) > 0) {
                throw new IllegalArgumentException(HIGHER_THAN_MAX);
            }
            validationCheckResult = IntegerValidationCheckResult.success(target, this);
        } catch (final NumberFormatException e) {
            validationCheckResult = IntegerValidationCheckResult.error(
                    target,
                    target.getInternationalizedKey("invalidInteger"), ImmutableMap.of(
                            "target", target,
                            "value", value)
            );
        } catch (final IllegalArgumentException e) {
            validationCheckResult = IntegerValidationCheckResult.error(
                    target,
                    target.getInternationalizedKey("badIntervalInteger"), ImmutableMap.of(
                            "target", target,
                            "value", value,
                            "type", e.getMessage(),
                            "bound", Objects.requireNonNull(LOWER_THAN_MIN.equals(e.getMessage()) ? min : max)
                    )
            );
        }
        return validationCheckResult;
    }

    @Override
    public FieldType toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final IntegerType integerType = clone.get();
        integerType.value = value;
        return integerType;
    }
    @Override
    public String toString() {
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        if(value==null){
            gen.writeNull();
            return;
        }
        gen.writeNumber(value);
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
        arrayNode.add((int) value);

    }

    @Override
    public Object toJsonForFrontend() {
        return value;
    }
}
