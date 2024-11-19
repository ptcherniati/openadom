package fr.inra.oresing.domain.data.deposit.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import lombok.Getter;
import lombok.Value;

import java.util.Map;

@Value
public class DefaultValidationCheckResult implements ValidationCheckResult {

    @Getter
    ValidationLevel level;

    @Getter
    String message;

    @Getter
    Map<String, Object> messageParams;

    public DefaultValidationCheckResult(final ValidationCheckResult validationCheckResult) {
        this(
                validationCheckResult.level(),
                validationCheckResult.message(),
                validationCheckResult.messageParams(),
                validationCheckResult.target()
        );
    }

    public DefaultValidationCheckResult(final ValidationLevel level, final String message, final Map<String, Object> messageParams, final CheckerTarget target) {
        super();
        this.level = level;
        this.message = message;
        this.messageParams = messageParams;
        this.target = target;
    }

    CheckerTarget target;

    @JsonIgnore
    public static ValidationCheckResult success(final CheckerTarget target) {
        return new DefaultValidationCheckResult(ValidationLevel.SUCCESS, null, null, target);
    }

    @JsonIgnore
    public static ValidationCheckResult warn(final String message, final ImmutableMap<String, Object> messageParams, final CheckerTarget target) {
        return new DefaultValidationCheckResult(ValidationLevel.WARN, message, messageParams, target);
    }

    @JsonIgnore
    public static ValidationCheckResult error(final String message, final Map<String, Object> messageParams, final CheckerTarget target) {
        return new DefaultValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target);
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
}