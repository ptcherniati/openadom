package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;
import java.util.function.Consumer;

public record Validation(Consumer<ValidationParams> buildError, String path, Map<String, Object> params) {
    void buildError(final ConfigurationException exception) {
        buildError().accept(new ValidationParams(exception, Map.of(), path()));
    }

    void buildError(final ConfigurationException exception, final Map<String, Object> params) {
        buildError().accept(new ValidationParams(exception, params, path()));
    }
}
