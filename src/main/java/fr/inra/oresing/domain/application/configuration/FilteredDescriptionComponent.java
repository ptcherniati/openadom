package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record FilteredDescriptionComponent(ComponentDescriptionType type,
                                           String componentKey,
                                           Set<Tag> tags,
                                           String submissionAuthorizationScope) implements ComponentDescription {
    public ComponentDescription withSubmission(final String submission) {
        throw new IllegalArgumentException("never used");
    }

    @Override
    public String exportHeaderName() {
        return null;
    }

    @Override
    public CheckerDescription checker() {
        return null;
    }

    @Override
    public List<Locale> langRestrictions() {
        throw new NotImplementedException();
    }
}
