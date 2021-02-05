package fr.inra.oresing.model;

import lombok.Value;

@Value
public class VariableComponentKey {
    String variable;
    String component;

    public String getId() {
        return variable + "_" + component;
    }
}
