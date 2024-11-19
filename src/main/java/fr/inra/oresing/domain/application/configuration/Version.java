package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;

public record Version(String version) implements Comparable<Version> {

    public static final Version FIRST_VERSION = new Version("1.0.1");
    public static final Version BAD_VERSION = new Version("1.0.1-BADVERSION");
    private static final String BAD_VERSION_FORMAT = "BAD_VERSION_FORMAT";

    public Version(final String version) {
        this.version = version.trim();
        getRunTimeVersion();
    }

    @Override
    public int compareTo(final Version otherVersion) {
        final Runtime.Version otherRuntimeVersion;
        try {
            otherRuntimeVersion = Runtime.Version.parse(otherVersion.version());
        } catch (final IllegalArgumentException e) {
            return 1;
        }
        return getRunTimeVersion().compareTo(otherRuntimeVersion);
    }

    public Runtime.Version getRunTimeVersion() {
        try {
            return Runtime.Version.parse(version());
        } catch (final IllegalArgumentException e) {
            throw new SiOreConfigurationFormatException(ConfigurationException.BAD_VERSION_PATTERN, Map.of("givenVersion", version()));
        }
    }
}
