package fr.inra.oresing.domain.application.configuration;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record ApplicationDescription(
        String name,
        Version version,
        Locale defaultLanguage,
        String comment
) {
    public static final String MISSING_NAME_APPLICATION = "MISSING_NAME_APPLICATION";
    public static final String UNSUPPORTED_NAME_APPLICATION = "UNSUPPORTED_NAME_APPLICATION";

    public ApplicationDescription(final String name, final Version version, final Locale defaultLanguage, final String comment) {
        Objects.requireNonNull(name);
        if ("null".equals(name)) {
            throw new IllegalArgumentException(MISSING_NAME_APPLICATION);
        } else if (!Configuration.getIsValidIdentifierPattern(2, 40).test(name.strip())) {
            throw new IllegalArgumentException(UNSUPPORTED_NAME_APPLICATION);
        }
        this.name = name.strip();
        this.version = version;
        this.defaultLanguage = Optional.ofNullable(defaultLanguage).orElse(Locale.FRENCH);
        this.comment = Optional.ofNullable(comment).orElse("");
    }
}
