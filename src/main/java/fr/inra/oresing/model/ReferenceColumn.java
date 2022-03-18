package fr.inra.oresing.model;

import fr.inra.oresing.checker.CheckerTarget;
import lombok.Value;

@Value
public class ReferenceColumn implements CheckerTarget, SomethingToBeStoredAsJsonInDatabase<String> {
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

    @Override
    public String getInternationalizedKey(String key) {
        return key + "WithColumn";
    }

    @Override
    public CheckerTargetType getType() {
        return CheckerTargetType.PARAM_COLUMN;
    }
}
