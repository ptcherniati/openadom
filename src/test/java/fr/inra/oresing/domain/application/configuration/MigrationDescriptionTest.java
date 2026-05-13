package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Tag("core.config")
@Tag("domain.model")
class MigrationDescriptionTest {

    @Test
    void testMigrationDescriptionBuilder() {
        Map<String, ComponentDescription> components = Map.of("comp1", ComponentDescriptionBuilder.basicComponent().build());

        MigrationDescription migration = new MigrationDescriptionBuilder()
                .components(components)
                .dataGroup("dataGroup")
                .build();

        Assertions.assertEquals(components, migration.components());
        Assertions.assertEquals("dataGroup", migration.dataGroup());
    }
}