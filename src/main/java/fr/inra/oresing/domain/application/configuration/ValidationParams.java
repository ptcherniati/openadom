package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;

public record ValidationParams(ConfigurationException exception, Map<String, Object> params, String path) {
}
