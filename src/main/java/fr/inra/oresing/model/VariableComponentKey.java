package fr.inra.oresing.model;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
public class VariableComponentKey {

    private static final String SEPARATOR = "_";

    String variable;
    String component;

    public static VariableComponentKey parseId(String variableComponentKeyId) {
        String variable = StringUtils.substringBefore(variableComponentKeyId, SEPARATOR);
        String component = StringUtils.substringAfter(variableComponentKeyId, SEPARATOR);
        return new VariableComponentKey(variable, component);
    }

    public String getId() {
        return variable + SEPARATOR + component;
    }
}