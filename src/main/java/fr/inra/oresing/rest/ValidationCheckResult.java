package fr.inra.oresing.rest;

import fr.inra.oresing.ValidationLevel;

import java.util.Map;

public interface ValidationCheckResult {

    ValidationLevel getLevel();

    String getMessage();

    Map<String, Object> getMessageParams();

    default boolean isSuccess() {
        return getLevel().isSuccess();
    }

    default boolean isError() {
        return getLevel().isError();
    }
}
