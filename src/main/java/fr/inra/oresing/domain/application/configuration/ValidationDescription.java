package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

import java.util.Set;

public record ValidationDescription(java.util.Map<String, CheckerDescription> checkers, Set<Tag> tags,
                                    Set<String> columns, boolean required, ComponentPresenceConstraint mandatory) {
}
