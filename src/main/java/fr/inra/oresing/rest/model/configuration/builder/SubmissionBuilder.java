package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationSubmissionComponent;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SubmissionBuilder(RootBuilder rootBuilder) {

    Parsing<Submission> buildSubmission(
            I18n i18n,
            final String dataKey,
            final JsonNode jsonNode,
            final Map<String, ComponentDescription> componentDescriptions) {
        final JsonNode oaSubmission = jsonNode.get(ConfigurationSchemaNode.OA_SUBMISSION);
        if (oaSubmission == null) {
            return new Parsing<>(i18n, null);
        }
        SubmissionType oaStrategy = null;
        try {
            oaStrategy = Optional.ofNullable(oaSubmission.get(ConfigurationSchemaNode.OA_STRATEGY))
                    .map(j -> rootBuilder.getMapper().convertValue(j, SubmissionType.class))
                    .orElse(SubmissionType.OA_INSERTION);
        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNKNOWN_STRATEGY_SUBMISSION, Map.of(
                            "declaredStrategy", oaSubmission.get(ConfigurationSchemaNode.OA_STRATEGY).textValue(),
                            "allStrategy", Stream.of(SubmissionType.values())
                                    .map(SubmissionType::name)
                                    .collect(Collectors.toCollection(TreeSet::new))),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            dataKey,
                            ConfigurationSchemaNode.OA_SUBMISSION,
                            ConfigurationSchemaNode.OA_STRATEGY
                    )
            );
        }
        final JsonNode oaAuthorization = oaSubmission.get(ConfigurationSchemaNode.OA_SUBMISSION_SCOPE);
        final Parsing<Submission.SubmissionScope> submissionScope = buildSubmissionScope(i18n, dataKey, oaAuthorization, componentDescriptions);
        i18n = submissionScope.i18n();
        final Submission.SubmissionScope authorization = submissionScope.result();
        final JsonNode oaFileName = oaSubmission.get(ConfigurationSchemaNode.OA_FILE_NAME);
        final Set<String> referenceScopesLabels = authorization.componentNames();
        final Parsing<Submission.SubmissionFileNameParsing> submissionFileNameParsing = rootBuilder.getSubmissionFileNameBuilder().build(i18n, oaFileName, componentDescriptions, referenceScopesLabels);
        i18n = submissionFileNameParsing.i18n();
        final Submission.SubmissionFileNameParsing submissionFileName = submissionFileNameParsing.result();
        return new Parsing<>(i18n, new Submission(oaStrategy, submissionFileName, authorization));
    }

    Parsing<Submission.SubmissionScope> buildSubmissionScope(I18n i18n,
                                                             final String dataKey,
                                                             final JsonNode authorizationNode,
                                                             final Map<String, ComponentDescription> componentDescriptions) {
        if (authorizationNode == null) {
            return new Parsing<>(i18n, null);
        }
        final String dataPath = NodeSchemaValidator.joinPath(
                ConfigurationSchemaNode.OA_DATA,
                dataKey
        );
        final Parsing<List<Submission.SubmissionScope.ReferenceScope>> referenceScopeParsing =
                buildReferenceScopes(
                        i18n,
                        (ArrayNode) authorizationNode.get(ConfigurationSchemaNode.OA_REFERENCE_SCOPES),
                        dataKey,
                        componentDescriptions);
        i18n = referenceScopeParsing.i18n();
        final Parsing<Submission.SubmissionScope.TimeScope> timeScopeParsing = buildTimeScopeScope(i18n,
                dataKey,
                authorizationNode.get(ConfigurationSchemaNode.OA_TIME_SCOPE),
                ImmutableMap.copyOf(componentDescriptions),
                dataPath);
        i18n = timeScopeParsing.i18n();
        return new Parsing<>(i18n, new Submission.SubmissionScope(
                referenceScopeParsing.result(),
                timeScopeParsing.result()
        ));
    }

    Parsing<Submission.SubmissionScope.TimeScope> buildTimeScopeScope(final I18n i18n, final String dataKey, final JsonNode timeScope, final ImmutableMap<String, ComponentDescription> componentDescription,
                                                                      final String dataPath) {

        if (timeScope == null) {
            return new Parsing<>(i18n, null);
        }
        final String component = Optional.ofNullable(timeScope.get(ConfigurationSchemaNode.OA_COMPONENT))
                .map(JsonNode::asText)
                .orElseThrow(() -> new IllegalArgumentException(ConfigurationException.MISSING_COMPONENT_FOR_TIMESCOPE_SUBMISSION.name()));
        Set<String> listComponentKeys = rootBuilder.checkers
                .getOrDefault(CheckerDescription.CheckerDescriptionType.DateChecker, new HashMap<>())
                .getOrDefault(dataKey, new HashMap<>())
                .keySet();
        if ("null".equals(component)) {
            rootBuilder.buildError(ConfigurationException.MISSING_COMPONENT_FOR_TIMESCOPE_SUBMISSION, Map.of(
                            "knownComponents", listComponentKeys),
                    NodeSchemaValidator.joinPath(
                            dataPath,
                            ConfigurationSchemaNode.OA_SUBMISSION,
                            ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                            ConfigurationSchemaNode.OA_TIME_SCOPE,
                            ConfigurationSchemaNode.OA_COMPONENT
                    )
            );
        } else if (!listComponentKeys.contains(component)) {
            rootBuilder.buildError(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME, Map.of(
                            "unknownComponent", component,
                            "knownComponents", listComponentKeys),
                    NodeSchemaValidator.joinPath(
                            dataPath,
                            ConfigurationSchemaNode.OA_SUBMISSION,
                            ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                            ConfigurationSchemaNode.OA_TIME_SCOPE,
                            ConfigurationSchemaNode.OA_COMPONENT
                    )
            );
        }
        return new Parsing<>(
                i18n,
                new Submission.SubmissionScope.TimeScope(component)
        );
    }

    Parsing<List<Submission.SubmissionScope.ReferenceScope>> buildReferenceScopes(
            I18n i18n,
            final ArrayNode referenceScopesNode,
            final String dataKey, Map<String, ComponentDescription> componentDescriptions) {
        if (referenceScopesNode == null) {
            return new Parsing<>(i18n, null);
        }
        final List<Submission.SubmissionScope.ReferenceScope> referenceScopes = new LinkedList<>();
        Integer index = 1;
        for (JsonNode referenceScopeNode : referenceScopesNode) {
            index++;

            String scopePath = NodeSchemaValidator.joinPath(
                    ConfigurationSchemaNode.OA_DATA,
                    dataKey,
                    ConfigurationSchemaNode.OA_SUBMISSION,
                    ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                    ConfigurationSchemaNode.OA_REFERENCE_SCOPES,
                    index.toString()
            );
            final String referenceScopeComponent = findAndtestComponentSection(referenceScopeNode, dataKey, index, scopePath);
            final String referenceScopeReference = findAndTestReferencesSection(dataKey, referenceScopeNode, referenceScopeComponent, index, scopePath);
            //TODO Lucile -----CF ticket n°197 : Verification des OA_references et OA_component dans referenceScopes-----
            if (Strings.isNullOrEmpty(referenceScopeReference)) {
                return new Parsing<>(i18n, List.of());
            }
            Submission.SubmissionScope.ReferenceScope referenceScope = new Submission.SubmissionScope.ReferenceScope(
                    referenceScopeReference,
                    referenceScopeComponent
            );
            referenceScope = rootBuilder().getSubmissionComponentResolver().resolveComponent(scopePath, referenceScope, componentDescriptions);


            try {
                i18n = i18n.add(
                        NodeSchemaValidator.joinI18nPath(
                                Internationalizations.DATA,
                                dataKey,
                                InternationalizationData.SUBMISSIONS,
                                InternationalizationSubmissionComponent.REFERENCE_SCOPES,
                                referenceScopeReference
                        ),
                        rootBuilder.getMapper().convertValue(referenceScopeNode.findPath(ConfigurationSchemaNode.OA_I_18_N), Map.class)
                );
            } catch (final IllegalArgumentException illegalArgumentException) {
                rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                        Map.of(),
                        NodeSchemaValidator.joinPath(
                                ConfigurationSchemaNode.OA_DATA,
                                dataKey,
                                ConfigurationSchemaNode.OA_SUBMISSION,
                                ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                                ConfigurationSchemaNode.OA_REFERENCE_SCOPES,
                                index.toString(),
                                ConfigurationSchemaNode.OA_I_18_N
                        ));
            }
            try {
                i18n = i18n.add(
                        NodeSchemaValidator.joinI18nPath(
                                ConfigurationSchemaNode.OA_DATA,
                                dataKey,
                                ConfigurationSchemaNode.OA_SUBMISSION,
                                ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                                ConfigurationSchemaNode.OA_REFERENCE_SCOPES,
                                index.toString(),
                                ConfigurationSchemaNode.OA_EXPORT_HEADER
                        ),
                        rootBuilder.getMapper().convertValue(referenceScopeNode.findPath(ConfigurationSchemaNode.OA_EXPORT_HEADER).findPath(ConfigurationSchemaNode.OA_I_18_N), Map.class)
                );
            } catch (final IllegalArgumentException illegalArgumentException) {
                rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                        Map.of(),
                        NodeSchemaValidator.joinPath(
                                ConfigurationSchemaNode.OA_DATA,
                                dataKey,
                                ConfigurationSchemaNode.OA_SUBMISSION,
                                ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                                ConfigurationSchemaNode.OA_REFERENCE_SCOPES,
                                index.toString(),
                                ConfigurationSchemaNode.OA_EXPORT_HEADER
                        ));
            }
            referenceScopes.add(referenceScope);
        }
        //referencecopes.fieldNames().forEachRemaining(componentNames::add);
        final ImmutableMap.Builder<String, Submission.SubmissionScope.ReferenceScope> builder = ImmutableMap.builder();
        return new Parsing<>(i18n, referenceScopes);
    }

    @Nullable
    private String findAndTestReferencesSection(final String dataKey, final JsonNode authorizationNode, final String authorizationScopeComponent, final Integer index,
                                                String scopePath) {
        final String authorizationScopeReference = Optional.ofNullable(authorizationNode
                        .get(ConfigurationSchemaNode.OA_REFERENCE))
                .map(JsonNode::asText)
                .orElse(null);
        if (Strings.isNullOrEmpty(authorizationScopeReference) && authorizationScopeComponent == null) {
            rootBuilder.buildError(ConfigurationException.MISSING_REFERENCE_AND_COMPONENT_NAME, Map.of(
                            "allDataNames", rootBuilder.getListDataKeys(),
                            "knownComponents", rootBuilder.getListComponentKeys(dataKey)),
                    scopePath);
        } else if (authorizationScopeComponent == null) {
            if ("null".equals(authorizationScopeReference)) {
                rootBuilder.buildError(ConfigurationException.MISSING_REFERENCE_NAME, Map.of(
                                "allDataNames", rootBuilder.getListDataKeys()),
                        NodeSchemaValidator.joinPath(
                                scopePath,
                                ConfigurationSchemaNode.OA_REFERENCES
                        ));
            }
        } else if (
                rootBuilder().getCheckers()
                .entrySet()
                .stream()
                .filter(entry-> entry.getKey().equals(CheckerDescription.CheckerDescriptionType.DateChecker))
                .map(entry->entry.getValue().getOrDefault(dataKey, new HashMap<>()).keySet())
                .flatMap(Set::stream)
                .toList().contains(authorizationScopeReference)) {
            rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                            "referenceName", Objects.requireNonNull(authorizationScopeReference),
                            "allDataNames", rootBuilder.getListDataKeys()),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            dataKey,
                            ConfigurationSchemaNode.OA_SUBMISSION,
                            ConfigurationSchemaNode.OA_SUBMISSION_SCOPE,
                            ConfigurationSchemaNode.OA_REFERENCE_SCOPES,
                            index.toString(),
                            ConfigurationSchemaNode.OA_REFERENCES
                    ));
        }
        return authorizationScopeReference;
    }

    @Nullable
    private String findAndtestComponentSection(final JsonNode authorizationNode, String dataKey, Integer index, String scopePath) {
        String referencePath = NodeSchemaValidator.joinPath(
                scopePath,
                ConfigurationSchemaNode.OA_COMPONENT
        );
        final String component = Optional.ofNullable(authorizationNode.get(ConfigurationSchemaNode.OA_COMPONENT))
                .map(JsonNode::asText)
                .orElse(null);
        final List<String> listComponentKeys = rootBuilder().getCheckers()
                .entrySet()
                .stream()
                .filter(entry-> entry.getKey().equals(CheckerDescription.CheckerDescriptionType.ReferenceChecker))
                .map(entry->entry.getValue().getOrDefault(dataKey, new HashMap<>()).keySet())
                .flatMap(Set::stream)
                .toList();
        if (component != null) {
            if ("null".equals(component)) {
                rootBuilder.buildError(ConfigurationException.MISSING_COMPONENT_FOR_COMPONENT_NAME, Map.of(
                                "knownComponents", listComponentKeys),
                        referencePath
                );
            } else if (!listComponentKeys.contains(component)) {
                rootBuilder.buildError(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME, Map.of(
                                "unknownComponent", component,
                                "knownComponents", listComponentKeys),
                        referencePath
                );
            }
        }
        return component;
    }
}
