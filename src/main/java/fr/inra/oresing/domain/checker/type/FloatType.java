package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.FloatValidationCheckResult;


import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static fr.inra.oresing.domain.checker.type.FloatType.IntervalFloatErrors.LOWER_THAN_MIN;

public non-sealed class FloatType implements FieldType<Float> {
    final Supplier<FloatType> clone;
    private final Float min;
    private final Float max;
    Float value;
    public FloatType(final Float min, final Float max) {
        super();
        this.min = min;
        this.max = max;
        clone = () -> new FloatType(min, max);
    }

    public static FloatType of(final Float value) {
        final FloatType floatType = new FloatType(null, null);
        floatType.value = value;
        return floatType;
    }

    protected Float getMin() {
        return min;
    }

    protected Float getMax() {
        return max;
    }

    @Override
    public Float getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.NUMERIC;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        FloatValidationCheckResult validationCheckResult;
        final DataColumn target = lineChecker.target();
        try {
            this.value = Float.parseFloat(value.replace(",", "."));
            if (min != null && this.value.compareTo(min) < 0) {
                throw new IllegalArgumentException(LOWER_THAN_MIN.name());
            }
            if (max != null && this.value.compareTo(max) > 0) {
                throw new IllegalArgumentException(IntervalFloatErrors.HIGHER_THAN_MAX.name());
            }
            validationCheckResult = FloatValidationCheckResult.success(lineChecker.target(), this);
        } catch (final NumberFormatException e) {
            validationCheckResult = FloatValidationCheckResult.error(
                    target,
                    target.getInternationalizedKey("invalidFloat"),
                    ImmutableMap.of(
                            "component", target.column(),
                            "value", value));
        } catch (final IllegalArgumentException e) {
            IntervalFloatErrors intervalFloatErrors = IntervalFloatErrors.valueOf(e.getMessage());
            validationCheckResult = FloatValidationCheckResult.error(
                    target,
                    target.getInternationalizedKey(intervalFloatErrors.errorMessage),
                    ImmutableMap.of(
                            "component", target.column(),
                            "value", value,
                            "type", e.getMessage(),
                            "bound", intervalFloatErrors.getBound.apply(this)
                    )
            );
        }
        return validationCheckResult;
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public String toString() {
        return Optional.ofNullable(value).map(Object::toString).orElse("");
    }

    @Override
    public FieldType copy() {
        final FloatType floatType = clone.get();
        floatType.value = value;
        return floatType;
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        if (value == null) {
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
        arrayNode.add((float) value);

    }

    @Override
    public Object toJsonForFrontend() {
        return value;
    }

    enum IntervalFloatErrors {
        LOWER_THAN_MIN(Constants.BAD_INTERVAL_FLOAT, FloatType::getMin),
        HIGHER_THAN_MAX(Constants.BAD_INTERVAL_FLOAT, FloatType::getMax);

        private final String errorMessage;
        private final Function<FloatType, Float> getBound;

        IntervalFloatErrors(String errorMessage, Function<FloatType, Float> getBound) {
            this.errorMessage = errorMessage;
            this.getBound = getBound;
        }

        private static class Constants {
            public static final String BAD_INTERVAL_FLOAT = "badIntervalFloat";
        }
    }
}