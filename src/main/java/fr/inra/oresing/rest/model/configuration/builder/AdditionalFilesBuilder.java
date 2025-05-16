package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.AdditionalFileDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.FieldDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationAdditionalFile;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public record AdditionalFilesBuilder(RootBuilder rootBuilder) {

    Parsing<Map<String, AdditionalFileDescription>> buildAdditionalFiles(final JsonNode oaAdditionalFiles, I18n i18n) {
        I18n i18n1 = i18n;
        final Iterator<Map.Entry<String, JsonNode>> fieldsIterator = oaAdditionalFiles.fields();
        final ImmutableMap.Builder<String, AdditionalFileDescription> builder = new ImmutableMap.Builder<>();
        while (fieldsIterator.hasNext()) {
            final Map.Entry<String, JsonNode> additionalTypeEntry = fieldsIterator.next();
            final String additionalType = additionalTypeEntry.getKey();
            final JsonNode additionalTypeValue = additionalTypeEntry.getValue();
            I18n localI18n;
            try {
                localI18n = i18n1.add(
                        NodeSchemaValidator.joinI18nPath(
                                Internationalizations.ADDITIONAL_FILES,
                                additionalType,
                                InternationalizationAdditionalFile.I_18_N
                        ),
                        rootBuilder.getMapper()
                                .convertValue(additionalTypeValue.get(ConfigurationSchemaNode.OA_I_18_N),
                                        Map.class));
                final Parsing<ImmutableMap<String, FieldDescription>> oaFormat = Optional
                        .ofNullable(additionalTypeValue.get(ConfigurationSchemaNode.OA_FORM_FIELDS))
                        .map(JsonNode::fields)
                        .map(entryIterator -> rootBuilder.getFieldBuilder()
                                .build(ConfigurationSchemaNode.OA_ADDITIONAL_FILES, FieldDescription.FieldDescriptionType.AdditionalFileField, localI18n, entryIterator,
                                        NodeSchemaValidator.joinPath(
                                                ConfigurationSchemaNode.OA_ADDITIONAL_FILES,
                                                additionalType,
                                                ConfigurationSchemaNode.OA_FORM_FIELDS),
                                        NodeSchemaValidator.joinI18nPath(Internationalizations.ADDITIONAL_FILES, additionalType)
                                ))
                        .orElse(new Parsing<>(i18n1, null));
                builder.put(additionalType, new AdditionalFileDescription(oaFormat.result()));
                i18n1 = oaFormat.i18n();
            } catch (final IllegalArgumentException illegalArgumentException) {
                rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                        Map.of(),
                        NodeSchemaValidator.joinPath(
                                ConfigurationSchemaNode.OA_ADDITIONAL_FILES,
                                additionalType,
                                ConfigurationSchemaNode.OA_I_18_N
                        ));
            }
        }
        return new Parsing<>(i18n1, builder.build());
    }
}