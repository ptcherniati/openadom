package fr.inra.oresing.domain.exceptions.configuration;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class BadApplicationConfigurationException extends OreSiTechnicalException {

    private final ConfigurationException configurationException;

    private BadApplicationConfigurationException(final String message, final ConfigurationException configurationException) {
        super(message);
        this.configurationException = configurationException;
    }

}