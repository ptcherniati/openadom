package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.model.configuration.JacksonErrorParser;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.reactive.ReactiveProgression;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public record ConfigurationBuilder(RootBuilder rootBuilder) {

    public static <P extends ReactiveProgression.ChangeOrCreateApplicationProgression> Configuration build(final byte[] bytes, final P progression, final String comment) {

        final YAMLMapper mapper = YAMLMapper.builder().build();
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        final boolean hasErrors = false;
        JsonNode rootNode = null;
        DocumentContext documentContext = null;
        try {
            rootNode = mapper.readTree(bytes);
            documentContext = JsonPath.parse(mapper.writeValueAsString(rootNode));
        } catch (JsonParseException jpe) {
            progression.pushError(JacksonErrorParser.parse(jpe));
            progression.complete();
            return null;
        } catch (final IOException e) {
            progression.pushError(new ValidationError(ConfigurationException.INVALID_CONFIGURATION_FILE.getMessage()));
            progression.complete();
            return null;
        }
        final Configuration configuration = new RootBuilder(
                progression,
                rootNode,
                documentContext
        ).build(bytes, comment);
        return configuration;
    }
}
