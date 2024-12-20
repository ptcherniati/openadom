package fr.inra.oresing.domain.application;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.Version;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("SUITE")

class ConfigurationTest {

    @Test
    public void testVersion() {
        assertEquals("1.0.1-BADVERSION", Version.BAD_VERSION.version());
        assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, Configuration.OPEN_ADOM_VERSION.version());
        final String versionString = "2.3.4.42-SNAPSHOT+12-2024-01-11";
        final Version applicationVersion = new Version(versionString);
        final Runtime.Version version = applicationVersion.getRunTimeVersion();
        assertEquals("2.3.4.42-SNAPSHOT+12-2024-01-11", version.toString());
        assertEquals(2, version.feature());
        assertEquals(3, version.interim());
        assertEquals(4, version.update());
        assertEquals(42, version.patch());
        assertEquals("SNAPSHOT", version.pre().orElse("absent"));
        assertEquals(Optional.of(12), version.build());
        assertEquals("2024-01-11", version.optional().orElse("no optional"));
        assertEquals(List.of(2, 3, 4, 42), version.version());
    }

}