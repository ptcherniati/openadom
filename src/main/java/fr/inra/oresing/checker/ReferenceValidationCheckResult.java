package fr.inra.oresing.checker;

import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.util.Map;
import java.util.UUID;

@Value
public class ReferenceValidationCheckResult implements ValidationCheckResult {
    boolean valid;
    String message;
    Map<String, Object> messageParams;
    UUID referenceId;
}
