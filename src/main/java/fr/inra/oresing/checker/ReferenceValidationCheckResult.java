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
    String message;
    Map<String, Object> messageParams;
    UUID referenceId;

    public static ReferenceValidationCheckResult success(UUID referenceId) {
        return new ReferenceValidationCheckResult(ValidationLevel.SUCCESS, null, null, referenceId);
    }

    public static ReferenceValidationCheckResult error(String message, ImmutableMap<String, Object> messageParams) {
        return new ReferenceValidationCheckResult(ValidationLevel.ERROR, message, messageParams, null);
    }
}