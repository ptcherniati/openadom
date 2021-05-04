package fr.inra.oresing.rest;

import java.util.Map;

public interface ValidationCheckResult {

    boolean isValid();

    String getMessage();

    Map<String, Object> getMessageParams();
}
