package fr.inra.oresing.rest.exceptions.configuration;

import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.rest.ConfigurationParsingResult;

public class BadApplicationConfigurationException extends OreSiTechnicalException {

    private final ConfigurationParsingResult configurationParsingResult;

    private BadApplicationConfigurationException(String message, ConfigurationParsingResult configurationParsingResult) {
        super(message);
        this.configurationParsingResult = configurationParsingResult;
    }

    public static void check(ConfigurationParsingResult configurationParsingResult) throws BadApplicationConfigurationException {
        if (!configurationParsingResult.isValid()) {
            throw new BadApplicationConfigurationException("configuration invalide", configurationParsingResult);
        }
    }

    public ConfigurationParsingResult getConfigurationParsingResult() {
        return configurationParsingResult;
    }
}