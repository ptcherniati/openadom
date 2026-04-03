package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ConfigurationBuilder {

    private Version version = new Version("1.0.0");
    private Set<Tag> tags = Set.of();
    private Internationalizations i18n;
    private ApplicationDescription applicationDescription;
    private Map<String, StandardDataDescription> dataDescription = Map.of();
    private RightRequestDescription rightsRequest;
    private Map<String, AdditionalFileDescription> additionalFiles = Map.of();
    private SortedSet<Node> hierarchicalNodes = new TreeSet<>();
    private List<String> requiredAuthorizationsAttributes = List.of();

    public ConfigurationBuilder version(Version version) {
        this.version = version;
        return this;
    }

    public ConfigurationBuilder tags(Set<Tag> tags) {
        this.tags = tags;
        return this;
    }

    public ConfigurationBuilder i18n(Internationalizations i18n) {
        this.i18n = i18n;
        return this;
    }

    public ConfigurationBuilder applicationDescription(ApplicationDescription applicationDescription) {
        this.applicationDescription = applicationDescription;
        return this;
    }

    public ConfigurationBuilder dataDescription(Map<String, StandardDataDescription> dataDescription) {
        this.dataDescription = dataDescription;
        return this;
    }

    public ConfigurationBuilder rightsRequest(RightRequestDescription rightsRequest) {
        this.rightsRequest = rightsRequest;
        return this;
    }

    public ConfigurationBuilder additionalFiles(Map<String, AdditionalFileDescription> additionalFiles) {
        this.additionalFiles = additionalFiles;
        return this;
    }

    public ConfigurationBuilder hierarchicalNodes(SortedSet<Node> hierarchicalNodes) {
        this.hierarchicalNodes = hierarchicalNodes;
        return this;
    }

    public ConfigurationBuilder requiredAuthorizationsAttributes(List<String> requiredAuthorizationsAttributes) {
        this.requiredAuthorizationsAttributes = requiredAuthorizationsAttributes;
        return this;
    }

    public Configuration build() {
        return new Configuration(
                version,
                tags,
                i18n,
                applicationDescription,
                dataDescription,
                rightsRequest,
                additionalFiles,
                hierarchicalNodes,
                requiredAuthorizationsAttributes
        );
    }
}