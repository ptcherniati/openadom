package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("core.config")
class ConfigurationBuilderTest {

    @Test
    void build() {
        Version version = new Version("1.0.0");
        Set<fr.inra.oresing.domain.application.configuration.Tag> tags = Set.of();
        Internationalizations i18n = new Internationalizations();
        ApplicationDescription applicationDescription = new ApplicationDescription("test_app", version, Locale.ENGLISH, "test comment");
        Map<String, StandardDataDescription> dataDescription = Map.of();
        RightRequestDescription rightsRequest = new RightRequestDescription(Map.of());
        Map<String, AdditionalFileDescription> additionalFiles = Map.of();
        SortedSet<Node> hierarchicalNodes = new TreeSet<>();
        List<String> requiredAuthorizationsAttributes = List.of();

        Configuration configuration = new ConfigurationBuilder()
                .version(version)
                .tags(tags)
                .i18n(i18n)
                .applicationDescription(applicationDescription)
                .dataDescription(dataDescription)
                .rightsRequest(rightsRequest)
                .additionalFiles(additionalFiles)
                .hierarchicalNodes(hierarchicalNodes)
                .requiredAuthorizationsAttributes(requiredAuthorizationsAttributes)
                .build();

        assertEquals(version, configuration.version());
        assertEquals(tags, configuration.tags());
        assertEquals(i18n, configuration.i18n());
        assertEquals(applicationDescription, configuration.applicationDescription());
        assertEquals(dataDescription, configuration.dataDescription());
        assertEquals(rightsRequest, configuration.rightsRequest());
        assertEquals(additionalFiles, configuration.additionalFiles());
        assertEquals(hierarchicalNodes, configuration.hierarchicalNodes());
        assertEquals(requiredAuthorizationsAttributes, configuration.requiredAuthorizationsAttributes());
    }

    @Test
    void buildWithDefaultValues() {
        Configuration configuration = new ConfigurationBuilder().build();

        assertNotNull(configuration.version());
        assertEquals(new Version("1.0.0"), configuration.version());
        assertNotNull(configuration.tags());
        assertEquals(Set.of(), configuration.tags());
        assertEquals(null, configuration.i18n());
        assertEquals(null, configuration.applicationDescription());
        assertNotNull(configuration.dataDescription());
        assertEquals(Map.of(), configuration.dataDescription());
        assertEquals(null, configuration.rightsRequest());
        assertNotNull(configuration.additionalFiles());
        assertEquals(Map.of(), configuration.additionalFiles());
        assertNotNull(configuration.hierarchicalNodes());
        assertEquals(new TreeSet<>(), configuration.hierarchicalNodes());
        assertNotNull(configuration.requiredAuthorizationsAttributes());
        assertEquals(List.of(), configuration.requiredAuthorizationsAttributes());
    }

    @Test
    void buildMultipleTimes() {
        ConfigurationBuilder builder = new ConfigurationBuilder();

        Configuration configuration1 = builder.version(new Version("1.0.0")).build();
        assertEquals(new Version("1.0.0"), configuration1.version());

        Configuration configuration2 = builder.version(new Version("2.0.0")).build();
        assertEquals(new Version("2.0.0"), configuration2.version());
    }
}