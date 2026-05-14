package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import lombok.Getter;

import java.util.Map;

public non-sealed class DefaultCheckerValidationCheckResult<T extends FieldType<?>> implements CheckerValidationCheckResult<T> {

    @Getter
    final ValidationLevel level;

    @Getter
    final String message;

    @Getter
    final Map<String, Object> messageParams;

    final T value;
    final CheckerTarget target;

    public DefaultCheckerValidationCheckResult(final CheckerValidationCheckResult<T> validationCheckResult) {
        this(
                validationCheckResult.level(),
                validationCheckResult.message(),
                validationCheckResult.messageParams(),
                validationCheckResult.target(),
                validationCheckResult.value()
        );
    }

    public DefaultCheckerValidationCheckResult(final ValidationLevel level, final String message, final Map<String, Object> messageParams, final CheckerTarget target, final T value) {
        super();
        this.level = level;
        this.message = message;
        this.messageParams = messageParams;
        this.target = target;
        this.value = value;
    }

    public static <T extends FieldType<?>> DefaultCheckerValidationCheckResult<T> success(final CheckerTarget target, final T value) {
        return new DefaultCheckerValidationCheckResult<>(ValidationLevel.SUCCESS, null, null, target, value);
    }

    public static <T extends FieldType<?>> DefaultCheckerValidationCheckResult<T> warn(final String message, final ImmutableMap<String, Object> messageParams, final CheckerTarget target, final T value) {
        return new DefaultCheckerValidationCheckResult<>(ValidationLevel.WARN, message, messageParams, target, value);
    }

    public static <T extends FieldType<?>> DefaultCheckerValidationCheckResult<T> error(final String message, final Map<String, Object> messageParams, final CheckerTarget target) {
        return new DefaultCheckerValidationCheckResult<>(ValidationLevel.ERROR, message, messageParams, target, null);
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
    public T value() {
        return value;
    }
}