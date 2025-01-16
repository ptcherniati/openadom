package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.CheckerTarget;

public record DataColumnPatternQualifierValue(
        String qualifierComponentKey,
        String column
) implements CheckerTarget, SomethingToBeStoredAsJsonInDatabase<String> {

    @Override
    public String toJsonForDatabase() {
        return column;
    }

    @Override
    public String getInternationalizedKey(final String key) {
        return key + "WithComponent";
    }


    @Override
    public String toHumanReadableString() {
        return column;
    }
}
