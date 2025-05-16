package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.NullType;
import lombok.Getter;

import java.util.Map;

public non-sealed class DefaultCheckerValidationCheckResult implements CheckerValidationCheckResult {

    @Getter
    final ValidationLevel level;

    @Getter
    final String message;

    @Getter
    final Map<String, Object> messageParams;

    final FieldType<?> value;
    final CheckerTarget target;

    public DefaultCheckerValidationCheckResult(final CheckerValidationCheckResult validationCheckResult) {
        this(
                validationCheckResult.level(),
                validationCheckResult.message(),
                validationCheckResult.messageParams(),
                validationCheckResult.target(),
                validationCheckResult.value()
        );
    }

    public DefaultCheckerValidationCheckResult(final ValidationLevel level, final String message, final Map<String, Object> messageParams, final CheckerTarget target, final FieldType<?> value) {
        super();
        this.level = level;
        this.message = message;
        this.messageParams = messageParams;
        this.target = target;
        this.value = value;
    }

    public static DefaultCheckerValidationCheckResult success(final CheckerTarget target, final FieldType<?> value) {
        return new DefaultCheckerValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, value);
    }

    public static DefaultCheckerValidationCheckResult warn(final String message, final ImmutableMap<String, Object> messageParams, final CheckerTarget target, final FieldType<?> value) {
        return new DefaultCheckerValidationCheckResult(ValidationLevel.WARN, message, messageParams, target, value);
    }

    public static DefaultCheckerValidationCheckResult error(final String message, final Map<String, Object> messageParams, final CheckerTarget target) {
        return new DefaultCheckerValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target, NullType.INSTANCE);
    }

    @Override
    public ValidationLevel level() {
        return level;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Map<String, Object> messageParams() {
        return messageParams;
    }

    @Override
    public CheckerTarget target() {
        return target;
    }

    @Override
    public FieldType<?> value() {
        return value;
    }
}