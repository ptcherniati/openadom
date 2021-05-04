package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiException;

public class BadApplicationConfigurationException extends OreSiException {

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
