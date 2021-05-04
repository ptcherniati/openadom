package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;

@Value
public class DefaultValidationCheckResult implements ValidationCheckResult {
    boolean valid;
    String message;
    Map<String, Object> messageParams;
}
