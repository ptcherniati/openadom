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
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;

import java.io.IOException;
import java.io.InputStream;

public record ConfigurationBuilder(RootBuilder rootBuilder) {

    public static Configuration build(final InputStream inputStream, final ReactiveEventHelper eventHelper, final String comment) {

        final YAMLMapper mapper = YAMLMapper.builder().build();
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        JsonNode rootNode;
        DocumentContext documentContext;
        try {
            rootNode = mapper.readTree(inputStream);
            documentContext = JsonPath.parse(mapper.writeValueAsString(rootNode));
        } catch (JsonParseException jpe) {
            eventHelper.pushError(JacksonErrorParser.parse(jpe));
            eventHelper.complete();
            return null;
        } catch (final IOException e) {
            eventHelper.pushError(new ValidationError(ConfigurationException.INVALID_CONFIGURATION_FILE.getMessage()));
            eventHelper.complete();
            return null;
        }
        return new RootBuilder(
                eventHelper,
                rootNode,
                documentContext
        ).build(inputStream, comment);
    }
}