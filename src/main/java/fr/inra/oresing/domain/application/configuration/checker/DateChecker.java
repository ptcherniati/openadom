package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.ComponentDateType;
import fr.inra.oresing.domain.data.read.query.ComponentType;

import java.time.temporal.TemporalAccessor;

public record DateChecker(
        CheckerDescriptionType type,
        Multiplicity multiplicity,
        boolean required,
        String pattern,
        TemporalAccessor min,
        TemporalAccessor max,
        String duration
) implements CheckerDescription {
        public static final CheckerDescription BAD_CHECKER = new DateChecker(
                CheckerDescriptionType.DateChecker,
                null,
                false,
                null,
                null,
                null,
                null
        );
    @Override
    public String comment() {
        return "%s Date".formatted(pattern());
    }
    @Override
    public String buildImportDataExempleForheader() {
        return "a date with pattern %s".formatted(pattern());
    }
}
