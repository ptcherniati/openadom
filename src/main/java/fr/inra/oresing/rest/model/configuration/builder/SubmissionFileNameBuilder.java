package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record SubmissionFileNameBuilder(RootBuilder rootBuilder) {
    Parsing<Submission.SubmissionFileNameParsing> build(final I18n i18n, final JsonNode filenameNode, final Map<String, ComponentDescription> componentDescription, final Set<String> listReferenceScope) {
        if (filenameNode == null) {
            return new Parsing<>(i18n, null);
        }
        final String pattern = Optional.ofNullable(filenameNode.get(ConfigurationSchemaNode.OA_FILE_PATTERN))
                .map(JsonNode::asText)
                .orElseThrow(() -> new IllegalArgumentException("MISSING_PATTERN_FOR_FILENAME_SUBMISSION"));
        List<String> referenceScope = Optional.ofNullable(filenameNode.get(ConfigurationSchemaNode.OA_MATCH_PATTERN_SCOPES))
                .map(j -> rootBuilder.getMapper().convertValue(j, List.class))
                .orElse(List.of());
        int startDate = -1;
        int endDate = -1;
        for (int i = 0; i < referenceScope.size(); i++) {
            String key = referenceScope.get(i);
            if (ConfigurationSchemaNode.OA_START_DATE_MATCH_PATTERN.equals(key)) {
                startDate = i + 1;
                continue;
            }
            if (ConfigurationSchemaNode.OA_END_DATE_MATCH_PATTERN.equals(key)) {
                endDate = i + 1;
                continue;
            }
            if (!listReferenceScope.contains(key)) {
                rootBuilder.buildError(ConfigurationException.UNKNOWN_NAME_REFERENCE_SCOPE, Map.of(
                                "unknownAuthorizationScope", key,
                                "knownAuthorizationScope", listReferenceScope),
                        "OA_submission > OA_fileName > OA_referenceScopes > %1$s".formatted(key));
            }
        }
        referenceScope = referenceScope.stream().filter(element->!element.startsWith("__")).toList();
        return new Parsing<>(i18n, new Submission.SubmissionFileNameParsing(pattern, referenceScope, startDate, endDate));
    }
}