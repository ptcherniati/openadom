package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record BasicComponent(
        ComponentDescriptionType type,
        String componentKey,
        ComputationChecker defaultValue,
        Set<Tag> tags,
        String importHeader,
        String exportHeaderName,
        List<Locale> langRestrictions,
        boolean required,
        ComponentPresenceConstraint mandatory,
        CheckerDescription checker,
        String submissionAuthorizationScope
) implements ComponentDescription {
    @Override
    public ComponentDescription withSubmission(final String submission) {
        return new BasicComponent(type(),
                componentKey(),
                defaultValue(),
                tags(),
                importHeader(),
                exportHeaderName(),
                langRestrictions(),
                required(),
                mandatory(),
                checker(),
                submission);
    }

    @Override
    public String buildImportHeaderForComponent() {
        return importHeader();
    }

    @Override
    public String buildImportDataExempleForComponent() {
        return Optional.ofNullable(checker())
                .map(CheckerDescription::buildImportDataExempleForheader)
                .orElse("A string");
    }
}
