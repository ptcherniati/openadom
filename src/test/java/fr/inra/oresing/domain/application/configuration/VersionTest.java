package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class VersionTest {

    @Test
    void testVersionBuilder() {
        Version version = new VersionBuilder().version("2.0.0").build();
        Assertions.assertEquals("2.0.0", version.version());
    }
}