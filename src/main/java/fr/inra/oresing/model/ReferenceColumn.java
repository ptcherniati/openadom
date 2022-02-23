package fr.inra.oresing.model;

import lombok.Value;

@Value
public class ReferenceColumn {
    String column;

    public String asString() {
        return column;
    }
}
