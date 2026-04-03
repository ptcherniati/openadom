package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

@Tag("core.config")
class ApplicationDescriptionTest {

    @Test
    void testApplicationDescriptionBuilder() {
        Version version = new VersionBuilder().build();
        ApplicationDescription description = new ApplicationDescriptionBuilder()
                .name("test_app")
                .version(version)
                .defaultLanguage(Locale.ENGLISH)
                .comment("test comment")
                .build();

        Assertions.assertEquals("test_app", description.name());
        Assertions.assertEquals(version, description.version());
        Assertions.assertEquals(Locale.ENGLISH, description.defaultLanguage());
        Assertions.assertEquals("test comment", description.comment());
    }
}