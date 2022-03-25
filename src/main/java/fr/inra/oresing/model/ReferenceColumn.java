package fr.inra.oresing.model;

import lombok.Value;

import java.util.Locale;

@Value
public class ReferenceColumn implements SomethingToBeStoredAsJsonInDatabase<String> {
    String column;

    public static ReferenceColumn forDisplay(Locale locale) {
        return new ReferenceColumn("__display_" + locale.toLanguageTag());
    }

    public String asString() {
        return column;
    }

    @Override
    public String toJsonForDatabase() {
        return column;
    }
}
