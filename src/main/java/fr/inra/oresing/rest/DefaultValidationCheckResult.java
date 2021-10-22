package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import lombok.Value;

import java.util.Map;

@Value
public class DefaultValidationCheckResult implements ValidationCheckResult {

    private static final DefaultValidationCheckResult SUCCESS_SINGLETON = new DefaultValidationCheckResult(ValidationLevel.SUCCESS, null, null);

    ValidationLevel level;

    String message;

    Map<String, Object> messageParams;

    public static ValidationCheckResult success() {
        return SUCCESS_SINGLETON;
    }

    public static ValidationCheckResult warn(String message, ImmutableMap<String, Object> messageParams) {
        return new DefaultValidationCheckResult(ValidationLevel.WARN, message, messageParams);
    }

    public static ValidationCheckResult error(String message, ImmutableMap<String, Object> messageParams) {
        return new DefaultValidationCheckResult(ValidationLevel.ERROR, message, messageParams);
    }
}