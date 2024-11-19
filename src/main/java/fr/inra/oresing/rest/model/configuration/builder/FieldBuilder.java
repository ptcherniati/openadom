package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public record FieldBuilder(RootBuilder rootBuilder) {

    <FD extends FieldDescription> Parsing<ImmutableMap<String, FD>> build(
            final String fieldpath,
            final FieldDescription.FieldDescriptionType type,
            I18n i18n,
            final Iterator<Map.Entry<String, JsonNode>> iterator,
            final String path,
            final String i18nPath) {
        final ImmutableMap.Builder<String, FD> fields = new ImmutableMap.Builder<String, FD>();
        int index = 0;
        while (iterator.hasNext()) {
            final Map.Entry<String, JsonNode> entry = iterator.next();
            final String fieldKey = entry.getKey();
            final JsonNode fieldNode = entry.getValue();
            try {
                i18n = i18n.add(NodeSchemaValidator.joinI18nPath(i18nPath, Internationalizations.FIELDS, fieldKey),
                        rootBuilder.getMapper().convertValue(fieldNode.findPath(ConfigurationSchemaNode.OA_I_18_N), Map.class));
            } catch (final IllegalArgumentException illegalArgumentException) {
                rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                        Map.of(),
                        NodeSchemaValidator.joinPath(
                                path, fieldKey));
            }
            final boolean required = Optional.ofNullable(fieldNode.get(ConfigurationSchemaNode.OA_REQUIRED))
                    .map(JsonNode::asBoolean)
                    .orElse(false);
            final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder().build(
                    i18n,
                    fieldKey,
                    required,
                    fieldpath,
                    fieldNode.get(ConfigurationSchemaNode.OA_CHECKER),
                    null);
            i18n = checkerDescriptionParsing.i18n();
            FD fieldDescription = switch (type) {
                case RightsRequestField ->
                        (FD) new RightsRequestField(index++, type, required, checkerDescriptionParsing.result());
                case AdditionalFileField ->
                        (FD) new AdditionalFileField(index++, type, required, checkerDescriptionParsing.result());
            };
            fields.put(fieldKey, fieldDescription);
        }
        return new Parsing<>(i18n, fields.build());
    }
}