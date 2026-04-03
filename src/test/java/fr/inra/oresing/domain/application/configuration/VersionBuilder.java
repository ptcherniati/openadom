package fr.inra.oresing.domain.application.configuration;

public class VersionBuilder {

    private String version = "1.0.0";

    public VersionBuilder version(String version) {
        this.version = version;
        return this;
    }

    public Version build() {
        return new Version(version);
    }
}