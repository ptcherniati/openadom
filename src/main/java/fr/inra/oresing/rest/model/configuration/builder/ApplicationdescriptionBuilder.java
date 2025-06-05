package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Version;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Locale;
import java.util.Map;

public record ApplicationdescriptionBuilder(RootBuilder rootBuilder) {

    Parsing<ApplicationDescription> build(final JsonNode applicationNode, I18n i18n, String comment) {
        I18n i18n1 = i18n;
        final JsonNode internationalization = applicationNode.get(ConfigurationSchemaNode.OA_I_18_N);
        Version version;
        try {
            version = new Version(applicationNode.findPath(ConfigurationSchemaNode.OA_VERSION).asText());
        } catch (final SiOreConfigurationFormatException e) {
            rootBuilder.buildError(ConfigurationException.BAD_VERSION_PATTERN, e.getParams(), ConfigurationSchemaNode.OA_APPLICATION);
            version = Version.BAD_VERSION;
        }
        final Locale locale = Locale.of(applicationNode.findPath(ConfigurationSchemaNode.OA_DEFAULT_LANGUAGE).asText("fr"));
        final String name = applicationNode.findPath(ConfigurationSchemaNode.OA_NAME).asText();
        String comment1 = Strings.isNullOrEmpty(comment) ? applicationNode.findPath(ConfigurationSchemaNode.OA_COMMENT).asText("no comment") : comment;
        try {
            i18n1 = i18n1.add(
                    Internationalizations.APPLICATION,
                    rootBuilder.getMapper().convertValue(internationalization, Map.class));
        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                    Map.of(),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_APPLICATION,
                            ConfigurationSchemaNode.OA_I_18_N
                    )
            );
        }
        final ApplicationDescription applicationDescription;
        try {
            applicationDescription = new ApplicationDescription(name, version, locale, comment1);
        } catch (final IllegalArgumentException e) {
            switch (e.getMessage()) {
                case "BAD_VERSION_NUMBER" ->
                        rootBuilder.buildError(ConfigurationException.UNSUPPORTED_VERSION_APPLICATION, ConfigurationSchemaNode.OA_APPLICATION);
                case "MISSING_VERSION_NUMBER" ->
                        rootBuilder.buildError(ConfigurationException.MISSING_VERSION_APPLICATION, ConfigurationSchemaNode.OA_APPLICATION);
                case "MISSING_NAME_APPLICATION" ->
                        rootBuilder.buildError(ConfigurationException.MISSING_NAME_APPLICATION, ConfigurationSchemaNode.OA_APPLICATION);
                case "UNSUPPORTED_NAME_APPLICATION" ->
                        rootBuilder.buildError(ConfigurationException.UNSUPPORTED_NAME_APPLICATION, Map.of("nameApplication", name), ConfigurationSchemaNode.OA_APPLICATION);
                case null, default ->
                        rootBuilder.buildError(ConfigurationException.UNSUPPORTED_VERSION_APPLICATION, ConfigurationSchemaNode.OA_APPLICATION);
            }
            return null;
        }
        return new Parsing<>(
                i18n1,
                applicationDescription
        );
    }
}