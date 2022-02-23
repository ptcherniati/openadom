package fr.inra.oresing.model;

import fr.inra.oresing.model.internationalization.InternationalizationImpl;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
public class VariableComponentKey extends InternationalizationImpl {

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