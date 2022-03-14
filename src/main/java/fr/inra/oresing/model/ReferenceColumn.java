package fr.inra.oresing.model;

import lombok.Value;

@Value
public class ReferenceColumn implements SomethingToBeStoredAsJsonInDatabase<String> {
    String column;

    public static ReferenceColumn forDisplay(String locale) {
        return new ReferenceColumn("__display_" + locale);
    }

    public String asString() {
        return column;
    }

    @Override
    public String toJsonForDatabase() {
        return column;
    }
}
