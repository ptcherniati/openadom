package fr.inra.oresing.model;

import lombok.Value;

@Value
public class VariableComponentReference {
    String variable;
    String component;

    public String getId() {
        return variable + "_" + component;
    }
}
