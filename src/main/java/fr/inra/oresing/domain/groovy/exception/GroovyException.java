package fr.inra.oresing.domain.groovy.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

import java.io.Serializable;
import java.util.Map;

public class GroovyException extends OreSiTechnicalException implements Serializable {
    public static final String DEFAULT_MESSAGE = "BAD_VALUE_FOR_EXPRESSION";

    final Map<String, Object> params;

    public GroovyException(String message) {
        this(message, null);
    }

    public GroovyException(String message, Map<String, Object> params) {
        super(message);
        this.params = params == null ? Map.of() : params;
    }

    public Map<String, Object> getParams() {
        return params == null ? Map.of() : params;
    }
}