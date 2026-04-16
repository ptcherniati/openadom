package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

import java.util.Map;
import java.util.Set;

public class ValidationDescriptionBuilder {

    private Map<String, CheckerDescription> checkers = Map.of();
    private Set<Tag> tags = Set.of();
    private Set<String> columns = Set.of();
    private boolean required = false;
    private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;

    public ValidationDescriptionBuilder checkers(Map<String, CheckerDescription> checkers) {
        this.checkers = checkers;
        return this;
    }

    public ValidationDescriptionBuilder tags(Set<Tag> tags) {
        this.tags = tags;
        return this;
    }

    public ValidationDescriptionBuilder columns(Set<String> columns) {
        this.columns = columns;
        return this;
    }

    public ValidationDescriptionBuilder required(boolean required) {
        this.required = required;
        return this;
    }

    public ValidationDescriptionBuilder mandatory(ComponentPresenceConstraint mandatory) {
        this.mandatory = mandatory;
        return this;
    }

    public ValidationDescription build() {
        return new ValidationDescription(checkers, tags, columns, required, mandatory);
    }
}