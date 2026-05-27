package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

@Tag("core.config")
class StandardDataDescriptionTest {

    @Test
    void testStandardDataDescriptionBuilder() {
        Set<fr.inra.oresing.domain.application.configuration.Tag> tags = Set.of(TagBuilder.dataTag());
        LinkedHashSet<String> naturalKey = new LinkedHashSet<>(List.of("key1"));
        Map<String, ComponentDescription> components = Map.of("comp1", ComponentDescriptionBuilder.basicComponent().build());
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder().build();
        Authorization authorization = AuthorizationBuilder.authorization().build();
        Map<String, ValidationDescription> validations = Map.of("val1", new ValidationDescriptionBuilder().build());
        List<Depends> depends = List.of(DependsBuilder.dependsParent().build());
        TreeMap<Integer, List<MigrationDescription>> migrations = new TreeMap<>(Map.of(1, List.of(new MigrationDescriptionBuilder().build())));

        StandardDataDescription description = new StandardDataDescriptionBuilder()
                .separator(',')
                .headerLine(2)
                .firstRowLine(3)
                .allowUnexpectedColumns(true)
                .tags(tags)
                .naturalKey(naturalKey)
                .componentDescriptions(components)
                .submission(submission)
                .authorization(authorization)
                .validations(validations)
                .depends(depends)
                .migrations(migrations)
                .build();

        Assertions.assertEquals(',', description.separator());
        Assertions.assertEquals(2, description.headerLine());
        Assertions.assertEquals(3, description.firstRowLine());
        Assertions.assertTrue(description.allowUnexpectedColumns());
        Assertions.assertEquals(tags, description.tags());
        Assertions.assertEquals(FilterModel.NONE, description.filterModel());
        Assertions.assertEquals(naturalKey, description.naturalKey());
        Assertions.assertEquals(components, description.componentDescriptions());
        Assertions.assertEquals(submission, description.submission());
        Assertions.assertEquals(authorization, description.authorization());
        Assertions.assertEquals(validations, description.validations());
        Assertions.assertEquals(depends, description.depends());
        Assertions.assertEquals(migrations, description.migrations());
    }

    @Test
    void testFilterModelDefaultsToNoneWhenNull() {
        StandardDataDescription description = new StandardDataDescription(
                ';', 1, 2, false, Set.of(), null, new LinkedHashSet<>(),
                Map.of(), null, null, Map.of(), List.of(), new TreeMap<>());

        Assertions.assertEquals(FilterModel.NONE, description.filterModel());
    }
}
