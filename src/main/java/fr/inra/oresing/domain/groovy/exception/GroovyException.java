package fr.inra.oresing.domain.groovy.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

import java.util.HashMap;
import java.util.Map;

public class GroovyException extends OreSiTechnicalException {
    public static final String DEFAULT_MESSAGE = "BAD_VALUE_FOR_EXPRESSION";

    Map<String, Object> params = new HashMap<>();

    public Map<String, Object> getParams() {
        return params==null?Map.of():params;
    }

    public GroovyException(String message) {
        this(message, null);
    }
    public GroovyException(String message, Map<String, Object> params) {
        super(message);
        this.params = params==null?Map.of():params;
    }
}
