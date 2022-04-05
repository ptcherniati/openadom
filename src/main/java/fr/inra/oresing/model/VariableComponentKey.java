package fr.inra.oresing.model;

import fr.inra.oresing.checker.CheckerTarget;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
public class VariableComponentKey implements CheckerTarget {

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

    @Override
    public String getInternationalizedKey(String key) {
        return key;
    }

    @Override
    public CheckerTargetType getType() {
        return CheckerTargetType.PARAM_VARIABLE_COMPONENT_KEY;
    }

    @Override
    public String toHumanReadableString() {
        return String.format("%s/%s", variable, component);
    }

    public String toSqlExtractPattern(){
        return String.format("aggreg.datavalues #>> '{%s,%s}'%n",variable.replaceAll("'", "''"), component.replaceAll("'", "''"));
    }
}