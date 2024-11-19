package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PatternComponentAdjacents(
        ComponentDescriptionType type,
        String componentKey,
        String importHeaderPattern,
        Set<Tag> tags,
        String exportHeaderName,
        boolean required,
        ComponentPresenceConstraint mandatory,
        List<Locale> langRestrictions,
        CheckerDescription checker) implements ComponentDescription {

    @Override
    public String submissionAuthorizationScope() {
        return "";
    }

    @Override
    public ComponentDescription withSubmission(String submission) {
        return null;
    }
}
