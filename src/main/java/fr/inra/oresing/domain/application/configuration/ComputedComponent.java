package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record ComputedComponent(ComponentDescriptionType type,
                                String componentKey,
                                Set<Tag> tags,
                                String exportHeaderName,
                                List<Locale> langRestrictions,
                                boolean required,
                                ComponentPresenceConstraint mandatory,
                                CheckerDescription checker,
                                ComputationChecker computationChecker,
                                String submissionAuthorizationScope) implements ComponentDescription {
    @Override
    public TransformationConfiguration transformation() {
        return computationChecker();
    }

    public ComponentDescription withSubmission(final String submission) {
        return new ComputedComponent(type(),
                componentKey(),
                tags(),
                exportHeaderName(),
                langRestrictions(),
                required(),
                mandatory(),
                checker(),
                computationChecker(),
                submission);
    }

}
