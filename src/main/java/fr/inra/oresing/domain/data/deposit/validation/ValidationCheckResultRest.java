package fr.inra.oresing.domain.data.deposit.validation;

import java.util.Map;

public record ValidationCheckResultRest(
        String type,
        String message,
        Map<String, Object> params,
        long lineNumber

) {
    public ValidationCheckResultRest(String type, String message, Map<String, Object> params) {
        this(type, message, params, -1);
    }

    public ValidationCheckResultRest withLineNumber(long lineNumber){
        return new ValidationCheckResultRest(type(), message(), params(), lineNumber());
    }
}
