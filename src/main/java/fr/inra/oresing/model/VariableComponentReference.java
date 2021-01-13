package fr.inra.oresing.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class VariableComponentReference {
    private String variable;
    private String component;
}
