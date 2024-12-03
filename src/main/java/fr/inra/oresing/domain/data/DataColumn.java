package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.CheckerTarget;

import java.util.Locale;

public record DataColumn(String column) implements CheckerTarget, SomethingToBeStoredAsJsonInDatabase<String> {

    public static final String DISPLAY = "__display_";
    public static final String DISPLAY_NAME = "%s%%s".formatted(DataColumn.DISPLAY);
    public static final String DISPLAY_DESCRIPTION = "%sdescription_%%s".formatted(DataColumn.DISPLAY);

    public static DataColumn forDisplayName(final Locale locale) {
        return forDisplayName(locale.toLanguageTag());
    }

    public static DataColumn forDisplayDescription(final Locale locale) {
        return forDisplayDescription(locale.toLanguageTag());
    }

    public static DataColumn forDisplayName(final String suffix) {
        return new DataColumn(DISPLAY_NAME.formatted(suffix));
    }

    public static DataColumn forDisplayDescription(final String suffix) {
        return new DataColumn(DISPLAY_DESCRIPTION.formatted(suffix));
    }

    public String asString() {
        return column;
    }

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
