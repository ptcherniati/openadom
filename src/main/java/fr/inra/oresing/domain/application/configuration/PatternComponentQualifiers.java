package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PatternComponentQualifiers(
        ComponentDescriptionType type,
        String componentKey,
        Set<Tag> tags,
        String exportHeaderName,
        List<Locale> langRestrictions,
        int patternNumber,
        CheckerDescription checker,
        String submissionAuthorizationScope) implements ComponentDescription {

    @Override
    public ComponentDescription withSubmission(final String submission) {
        return new PatternComponentQualifiers(type(),
                componentKey(),
                tags(),
                exportHeaderName(),
                langRestrictions(),
                patternNumber(),
                checker(),
                submission);
    }
}
