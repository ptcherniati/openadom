package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record DynamicComponent(ComponentDescriptionType type,
                               String componentKey,
                               ComputationChecker defaultValue,
                               String exportHeaderName,
                               List<Locale> langRestrictions,
                               Set<Tag> tags,
                               boolean required, ComponentPresenceConstraint mandatory, CheckerDescription checker,
                               String prefix,
                               String reference,
                               String referenceColumnToLookForHeader,
                               String submissionAuthorizationScope) implements ComponentDescription {
    public ComponentDescription withSubmission(final String submission) {
        return new DynamicComponent(type(),
                componentKey(),
                defaultValue(),
                exportHeaderName(),
                langRestrictions(),
                tags(),
                required(),
                mandatory(),
                checker(),
                prefix(),
                reference(),
                referenceColumnToLookForHeader(),
                submission);
    }

    @Override
    public String buildImportHeaderForComponent() {
        return "%sForColumn%sOfData%s".formatted(prefix(), referenceColumnToLookForHeader(), reference());
    }

    @Override
    public String buildImportDataExempleForComponent() {
        return Optional.ofNullable(checker())
                .map(CheckerDescription::buildImportDataExempleForheader)
                .orElse("A string");
    }
}
