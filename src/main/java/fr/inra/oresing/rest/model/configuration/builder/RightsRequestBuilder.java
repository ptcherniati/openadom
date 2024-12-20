package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.FieldDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationRightrequest;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;
import java.util.Optional;

public record RightsRequestBuilder(RootBuilder rootBuilder) {

    Parsing<RightRequestDescription> build(final JsonNode oaRightsRequest, I18n i18n) {
        I18n localI18n = i18n;
        try {
            localI18n = localI18n.add(
                    NodeSchemaValidator.joinI18nPath(
                            Internationalizations.RIGHT_REQUEST,
                            InternationalizationRightrequest.I_18_N
                    ),
                    rootBuilder.getMapper()
                            .convertValue(oaRightsRequest
                                            .get(ConfigurationSchemaNode.OA_I_18_N),
                                    Map.class)
            );
        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                    Map.of(),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_RIGHTS_REQUEST,
                            ConfigurationSchemaNode.OA_DESCRIPTION,
                            ConfigurationSchemaNode.OA_I_18_N
                    ));
        }
        final Parsing<ImmutableMap<String, FieldDescription>> oaFormat = Optional.ofNullable(oaRightsRequest.get(ConfigurationSchemaNode.OA_FORM_FIELDS))
                .map(JsonNode::fields)
                .map(entryIterator -> rootBuilder.getFieldBuilder()
                        .build(ConfigurationSchemaNode.OA_RIGHTS_REQUEST, FieldDescription.FieldDescriptionType.RightsRequestField, i18n, entryIterator, "rightsrequest.fields", Internationalizations.RIGHT_REQUEST))
                .orElse(new Parsing<>(localI18n, null));
        return new Parsing<>(oaFormat.i18n(), new RightRequestDescription(oaFormat.result()));
    }
}