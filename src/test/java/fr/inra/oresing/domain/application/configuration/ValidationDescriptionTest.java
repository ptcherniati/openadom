package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescriptionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

@org.junit.jupiter.api.Tag("core.config")
class ValidationDescriptionTest {

    @Test
    void testValidationDescriptionBuilder() {
        Map<String, CheckerDescription> checkers = Map.of("checker1", CheckerDescriptionBuilder.stringChecker().build());
        Set<Tag> tags = Set.of(TagBuilder.dataTag());
        Set<String> columns = Set.of("col1");

        ValidationDescription validation = new ValidationDescriptionBuilder()
                .checkers(checkers)
                .tags(tags)
                .columns(columns)
                .required(true)
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals(checkers, validation.checkers());
        Assertions.assertEquals(tags, validation.tags());
        Assertions.assertEquals(columns, validation.columns());
        Assertions.assertTrue(validation.required());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, validation.mandatory());
    }
}