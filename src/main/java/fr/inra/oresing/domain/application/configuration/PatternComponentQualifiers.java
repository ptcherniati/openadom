package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PatternComponentQualifiers(
        ComponentDescriptionType type,
        String componentKey,
        ComputationChecker defaultValue,
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
                defaultValue(),
                tags(),
                exportHeaderName(),
                langRestrictions(),
                patternNumber(),
                checker(),
                submission);
    }
}
