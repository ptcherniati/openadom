package fr.inra.oresing.model;

import fr.inra.oresing.checker.CheckerTarget;
import lombok.Value;

import java.util.Locale;

@Value
public class ReferenceColumn implements CheckerTarget, SomethingToBeStoredAsJsonInDatabase<String> {
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

    @Override
    public String getInternationalizedKey(String key) {
        return key + "WithColumn";
    }

    @Override
    public CheckerTargetType getType() {
        return CheckerTargetType.PARAM_COLUMN;
    }

    @Override
    public String toHumanReadableString() {
        return column;
    }
}
