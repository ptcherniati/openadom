package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.util.Map;
import java.util.UUID;

@Value
public class ReferenceValidationCheckResult implements ValidationCheckResult {
    ValidationLevel level;
    Object value;
    String message;
    Map<String, Object> messageParams;
    CheckerTarget target;
    UUID referenceId;

    public static ReferenceValidationCheckResult success(Object value, CheckerTarget target, UUID referenceId) {
        return new ReferenceValidationCheckResult(ValidationLevel.SUCCESS, value,  null, null, target, referenceId);
    }

    public static ReferenceValidationCheckResult error(String message, ImmutableMap<String, Object> messageParams) {
        return new ReferenceValidationCheckResult(ValidationLevel.ERROR,null,  message, messageParams, null, null);
    }
}