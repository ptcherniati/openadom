package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Locale;

public record ReferenceScopeComponent(ComponentDescriptionType type,
                                      String componentKey,
                                      String authorizationScopeName,
                                      String references,
                                      String component,
                                      String exportHeaderName,
                                      String submissionAuthorizationScope
) implements ComponentDescription {

    @Override
    public List<Locale> langRestrictions() {
        throw new NotImplementedException();
    }

    @Override
    public CheckerDescription checker() {
        return null;
    }

    public ComponentDescription withSubmission(final String submission) {
        throw new IllegalArgumentException("never used");
    }
}