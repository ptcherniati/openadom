package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;

import java.util.*;
import java.util.stream.Collectors;

public record PatternComponent(ComponentDescriptionType type,
                               String componentKey,
                               ComputationChecker defaultValue,
                               String exportHeaderName,
                               List<Locale> langRestrictions,
                               Set<Tag> tags,
                               boolean required,
                               ComponentPresenceConstraint mandatory,
                               CheckerDescription checker,
                               String patternForComponents,
                               Map<String, PatternComponentQualifiers> patternComponentQualifiers,
                               Map<String, PatternComponentAdjacents> patternComponentAdjacents,
                               String submissionAuthorizationScope) implements ComponentDescription {
    public ComponentDescription withSubmission(final String submission) {
        return new PatternComponent(type(),
                componentKey(),
                defaultValue(),
                exportHeaderName(),
                langRestrictions(),
                tags(),
                required(),
                mandatory(),
                checker(),
                patternForComponents(),
                patternComponentQualifiers(),
                patternComponentAdjacents(),
                submission);
    }

    public String buildImportHeaderForComponent() {
        String comment = patternComponentQualifiers().values().stream()
                .map(patternColumnComponent -> "%d : %s as %s".formatted(
                                patternColumnComponent.patternNumber(),
                                patternColumnComponent.componentKey(),
                                Optional.ofNullable(patternColumnComponent)
                                        .map(PatternComponentQualifiers::checker)
                                        .map(CheckerDescription::comment)
                                        .orElse("String")
                        )
                )
                .collect(Collectors.joining(";"));
        return "%s #%s".formatted(patternForComponents(), comment);
    }

    @Override
    public String buildImportDataExempleForComponent() {
        return Optional.ofNullable(checker())
                .map(CheckerDescription::buildImportDataExempleForheader)
                .orElse("A string");
    }

}
