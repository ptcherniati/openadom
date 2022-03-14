package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.GroovyConfiguration;
import fr.inra.oresing.checker.GroovyLineChecker;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ApplicationConfigurationService {
    public static final List INTERNATIONALIZED_FIELDS = List.of("internationalization", "internationalizationName", "internationalizedColumns", "internationalizationDisplay");

    Map<String, Map> getInternationalizedSections(Map<String, Object> toParse, List<IllegalArgumentException> exceptions) {
        Map<String, Map> parsedMap = new LinkedHashMap<>();
        Iterator<Map.Entry<String, Object>> iterator = toParse.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String key = entry.getKey();
            Object value = entry.getValue();
            if (INTERNATIONALIZED_FIELDS.contains(key)) {
                value = formatSection((Map<String, Object>) value, exceptions);
                parsedMap.put(key, (Map) value);
                iterator.remove();
            } else if (value instanceof Map) {
                Map<String, Map> internationalizedSections = getInternationalizedSections((Map<String, Object>) value, exceptions);
                if (!internationalizedSections.isEmpty()) {
                    parsedMap.put(key, internationalizedSections);
                }
            }
        }
        return parsedMap;
    }

    private Object formatSection(Map<String, Object> value, List<IllegalArgumentException> exceptions) {
        try {
            return new ObjectMapper().convertValue(value, Internationalization.class);
        } catch (IllegalArgumentException e) {
            Map<String, Object> internationalizationMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : value.entrySet()) {
                try {
                    internationalizationMap.put(entry.getKey(), new ObjectMapper().convertValue(entry.getValue(), Internationalization.class));
                } catch (IllegalArgumentException e2) {
                    try {
                        internationalizationMap.put(entry.getKey(), new ObjectMapper().convertValue(entry.getValue(), InternationalizationDisplay.class));
                    } catch (IllegalArgumentException e3) {
                        exceptions.add(e2);
                    }
                }
            }
            return internationalizationMap;
        }
    }

    ConfigurationParsingResult unzipConfiguration(MultipartFile file) {
        return null;
    }

    ConfigurationParsingResult parseConfigurationBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return ConfigurationParsingResult.builder()
                    .recordEmptyFile()
                    .build();
        }
        Map<String, Map> internationalizedSections = new HashMap<>();
        try {
            YAMLMapper mapper = new YAMLMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Versioned versioned = mapper.readValue(bytes, Versioned.class);
            int actualVersion = versioned.getVersion();
            int expectedVersion = 0;
            if (actualVersion != expectedVersion) {
                return ConfigurationParsingResult.builder()
                        .recordUnsupportedVersion(actualVersion, expectedVersion)
                        .build();
            }
        } catch (UnrecognizedPropertyException e) {
            return onUnrecognizedPropertyException(e);
        } catch (InvalidFormatException e) {
            return onInvalidFormatException(e);
        } catch (JsonProcessingException e) {
            return onJsonProcessingException(e);
        } catch (IOException e) {
            throw new OreSiTechnicalException("ne peut lire le fichier YAML", e);
        }

        Configuration configuration;
        try {
            YAMLMapper mapper = new YAMLMapper();
            Map<String, Object> mappedObject = (Map<String, Object>) mapper.readValue(bytes, Object.class);
            List<IllegalArgumentException> exceptions = List.of();
            internationalizedSections = getInternationalizedSections(mappedObject, exceptions);
            if (!exceptions.isEmpty()) {
                return onMappingExceptions(exceptions);
            }
            try {
                configuration = mapper.convertValue(mappedObject, Configuration.class);
                configuration.setInternationalization(mapper.convertValue(internationalizedSections, InternationalizationMap.class));
            } catch (IllegalArgumentException e) {
                if (e.getCause() instanceof UnrecognizedPropertyException) {
                    throw (UnrecognizedPropertyException) e.getCause();
                } else if (e.getCause() instanceof InvalidFormatException) {
                    throw (InvalidFormatException) e.getCause();
                } else if (e.getCause() instanceof JsonProcessingException) {
                    throw (JsonProcessingException) e.getCause();
                } else {
                    throw e;
                }
            }
        } catch (UnrecognizedPropertyException e) {
            return onUnrecognizedPropertyException(e);
        } catch (InvalidFormatException e) {
            return onInvalidFormatException(e);
        } catch (JsonProcessingException e) {
            return onJsonProcessingException(e);
        } catch (IOException e) {
            throw new OreSiTechnicalException("ne peut lire le fichier YAML", e);
        }
        return getConfigurationParsingResultForSyntacticallyValidYaml(configuration);
    }

    private ConfigurationParsingResult getConfigurationParsingResultForSyntacticallyValidYaml(Configuration configuration) {
        ConfigurationParsingResult.Builder builder = ConfigurationParsingResult.builder();
        Set<String> references = configuration.getReferences().keySet();
        ImmutableSet.Builder<String> requiredAuthorizationsAttributesBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.CompositeReferenceDescription> compositeReferenceEntry : configuration.getCompositeReferences().entrySet()) {
            verifyCompositeReferenceReferenceExists(configuration, builder, compositeReferenceEntry);
            verifyCompositeReferenceParentColumnExists(configuration, builder, compositeReferenceEntry);
            verifyCompositeReferenceParentRecursiveColumnExists(configuration, builder, compositeReferenceEntry);
        }

        for (Map.Entry<String, Configuration.ReferenceDescription> referenceEntry : configuration.getReferences().entrySet()) {
            verifyReferenceKeyColumns(configuration, builder, referenceEntry);
            verifyInternationalizedColumnsExists(configuration, builder, referenceEntry);
            verifyInternationalizedColumnsExistsForPattern(configuration, builder, referenceEntry);
            verifyValidationCheckersAreValids(configuration, builder, referenceEntry, references);
        }

        for (Map.Entry<String, Configuration.DataTypeDescription> entry : configuration.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            verifyDatatypeCheckersExists(builder, dataTypeDescription, dataType);
            verifyDatatypeCheckerReferenceRefersToExistingReference(builder, references, dataType, dataTypeDescription);
            verifyDatatypeCheckerGroovyExpressionExistsAndCanCompile(builder, dataTypeDescription);
            verifyInternationalizedColumnsExistsForPatternInDatatype(configuration, builder, dataType, dataTypeDescription);

            Configuration.AuthorizationDescription authorization = dataTypeDescription.getAuthorization();
            Set<String> variables = dataTypeDescription.getData().keySet();
            if (authorization == null) {
                /**
                 * to decomment if authorization section is required
                 */
                //builder.missingAuthorizationsForDatatype(dataType);
            } else {
                VariableComponentKey timeScopeVariableComponentKey = authorization.getTimeScope();
                verifyDatatypeTimeScopeExistsAndIsValid(builder, dataType, dataTypeDescription, variables, timeScopeVariableComponentKey);

                LinkedHashMap<String, VariableComponentKey> authorizationScopesVariableComponentKey = authorization.getAuthorizationScopes();
                verifyDatatypeAuthorizationScopeExistsAndIsValid(builder, dataType, configuration, variables, authorizationScopesVariableComponentKey);
                requiredAuthorizationsAttributesBuilder.addAll(authorizationScopesVariableComponentKey.keySet());
            }

            Multiset<String> variableOccurrencesInDataGroups = TreeMultiset.create();
            verifyDatatypeDataGroupsContainsExistingVariables(builder, dataTypeDescription, variables, variableOccurrencesInDataGroups);

            verifyDatatypeBindingToExistingVariableComponent(builder, variables, variableOccurrencesInDataGroups);
            verifyDatatypeBindingToExistingVariableComponent(builder, dataTypeDescription, variables);
            verifyChartDescription(builder, dataType, dataTypeDescription);
        }
        configuration.setRequiredAuthorizationsAttributes(List.copyOf(requiredAuthorizationsAttributesBuilder.build()));

        return builder.build(configuration);
    }

    private void verifyChartDescription(ConfigurationParsingResult.Builder builder, String datatype, Configuration.DataTypeDescription dataTypeDescription) {
        dataTypeDescription.getData().entrySet()
                .forEach(entry -> {
                    final String variable = entry.getKey();
                    final Configuration.Chart chartDescription = entry.getValue().getChartDescription();
                    if (chartDescription != null) {
                        final String valueComponent = chartDescription.getValue();
                        final LinkedHashMap<String, Configuration.VariableComponentDescription> components = entry.getValue().getComponents();
                        if (Strings.isNullOrEmpty(valueComponent)) {
                            builder.recordUndeclaredValueForChart(datatype, variable, components.keySet());
                        } else {
                            if (!components.containsKey(valueComponent)) {
                                builder.recordMissingValueComponentForChart(datatype, variable, valueComponent, components.keySet());
                            }
                            final VariableComponentKey aggregation = chartDescription.getAggregation();
                            if (aggregation != null) {
                                if (!dataTypeDescription.getData().containsKey(aggregation.getVariable())) {
                                    builder.recordMissingAggregationVariableForChart(datatype, variable, aggregation, dataTypeDescription.getData().keySet());
                                } else if (!dataTypeDescription.getData().get(aggregation.getVariable()).getComponents().containsKey(aggregation.getComponent())) {
                                    builder.recordMissingAggregationComponentForChart(datatype, variable, aggregation, components.keySet());
                                }

                            }
                            final String standardDeviation = chartDescription.getStandardDeviation();
                            if (standardDeviation != null && !components.containsKey(standardDeviation)) {
                                builder.recordMissingStandardDeviationComponentForChart(datatype, variable, standardDeviation, components.keySet());
                            }
                            final String unit = chartDescription.getUnit();
                            if (standardDeviation != null && !components.containsKey(unit)) {
                                builder.recordMissingUnitComponentForChart(datatype, variable, unit, components.keySet());
                            }
                        }
                    }
                });
    }

    private void verifyCompositeReferenceReferenceExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.CompositeReferenceDescription> compositeReferenceEntry) {
        String compositeReferenceName = compositeReferenceEntry.getKey();
        Configuration.CompositeReferenceDescription compositeReferenceDescription = compositeReferenceEntry.getValue();
        Set<String> expectingReferences = compositeReferenceDescription.getComponents()
                .stream()
                .map(crd -> crd.getReference())
                .filter(ref -> {
                    if (ref == null) {
                        builder.recordMissingReferenceInCompositereference(compositeReferenceName);
                    }
                    return ref != null;
                })
                .collect(Collectors.toSet());
        Set<String> existingReferences = configuration.getReferences().keySet();
        ImmutableSet<String> unknownReferences = Sets.difference(expectingReferences, existingReferences).immutableCopy();
        if (!unknownReferences.isEmpty()) {
            builder.recordUnknownReferenceInCompositeReference(compositeReferenceName, unknownReferences, existingReferences);
        }
    }

    private void verifyCompositeReferenceParentColumnExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.CompositeReferenceDescription> compositeReferenceEntry) {
        String compositeReferenceName = compositeReferenceEntry.getKey();
        Configuration.CompositeReferenceDescription compositeReferenceDescription = compositeReferenceEntry.getValue();
        String previousReference = null;
        for (Configuration.CompositeReferenceComponentDescription component : compositeReferenceDescription.getComponents()) {
            if (component.getReference() == null) {
                break;
            }
            String parentKeyColumn = component.getParentKeyColumn();
            if (previousReference == null && parentKeyColumn != null) {
                builder.recordRequiredReferenceInCompositeReferenceForParentKeyColumn(compositeReferenceName, parentKeyColumn);
            } else if (previousReference != null) {
                String reference = component.getReference();
                if (parentKeyColumn == null) {
                    builder.recordRequiredParentKeyColumnInCompositeReferenceForReference(compositeReferenceName, reference, previousReference);
                } else if (!configuration.getReferences().get(reference).getColumns().containsKey(parentKeyColumn)) {
                    builder.recordMissingParentColumnForReferenceInCompositeReferenceFor(compositeReferenceName, reference, parentKeyColumn);
                }
            }
            previousReference = component.getReference();
        }
    }

    private void verifyCompositeReferenceParentRecursiveColumnExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.CompositeReferenceDescription> compositeReferenceEntry) {
        String compositeReferenceName = compositeReferenceEntry.getKey();
        Configuration.CompositeReferenceDescription compositeReferenceDescription = compositeReferenceEntry.getValue();
        for (Configuration.CompositeReferenceComponentDescription component : compositeReferenceDescription.getComponents()) {
            String reference = component.getReference();
            if (reference == null || !configuration.getReferences().containsKey(reference)) {
                continue;
            }
            String parentRecursiveKey = component.getParentRecursiveKey();
            if (parentRecursiveKey != null && !configuration.getReferences().get(reference).getColumns().containsKey(parentRecursiveKey)) {
                builder.recordMissingParentRecursiveKeyColumnForReferenceInCompositeReference(compositeReferenceName, reference, parentRecursiveKey);
            }
        }
    }

    private void verifyDatatypeBindingToExistingVariableComponent(ConfigurationParsingResult.Builder builder, Configuration.DataTypeDescription dataTypeDescription, Set<String> variables) {
        for (Configuration.ColumnBindingDescription columnBindingDescription : dataTypeDescription.getFormat().getColumns()) {
            VariableComponentKey boundTo = columnBindingDescription.getBoundTo();
            String variable = boundTo.getVariable();
            if (variables.contains(variable)) {
                String component = boundTo.getComponent();
                Set<String> components = dataTypeDescription.getData().get(variable).getComponents().keySet();
                if (components.contains(component)) {
                    // OK
                } else {
                    builder.recordCsvBoundToUnknownVariableComponent(columnBindingDescription.getHeader(), variable, component, components);
                }
            } else {
                builder.recordCsvBoundToUnknownVariable(columnBindingDescription.getHeader(), variable, variables);
            }
        }
    }

    private void verifyDatatypeBindingToExistingVariableComponent(ConfigurationParsingResult.Builder builder, Set<String> variables, Multiset<String> variableOccurrencesInDataGroups) {
        variables.forEach(variable -> {
            int count = variableOccurrencesInDataGroups.count(variable);
            if (count == 0) {
                builder.recordUndeclaredDataGroupForVariable(variable);
            } else if (count > 1) {
                builder.recordVariableInMultipleDataGroup(variable);
            }
        });
    }

    private void verifyDatatypeDataGroupsContainsExistingVariables(ConfigurationParsingResult.Builder builder, Configuration.DataTypeDescription dataTypeDescription, Set<String> variables, Multiset<String> variableOccurrencesInDataGroups) {
        for (Map.Entry<String, Configuration.DataGroupDescription> dataGroupEntry : dataTypeDescription.getAuthorization().getDataGroups().entrySet()) {
            String dataGroup = dataGroupEntry.getKey();
            Configuration.DataGroupDescription dataGroupDescription = dataGroupEntry.getValue();
            Set<String> dataGroupVariables = dataGroupDescription.getData();
            variableOccurrencesInDataGroups.addAll(dataGroupVariables);
            ImmutableSet<String> unknownVariables = Sets.difference(dataGroupVariables, variables).immutableCopy();
            if (!unknownVariables.isEmpty()) {
                builder.recordUnknownVariablesInDataGroup(dataGroup, unknownVariables, variables);
            }
        }
    }

    private void verifyDatatypeAuthorizationScopeExistsAndIsValid(ConfigurationParsingResult.Builder builder, String dataType, Configuration configuration, Set<String> variables, LinkedHashMap<String, VariableComponentKey> authorizationScopesVariableComponentKey) {
        if (authorizationScopesVariableComponentKey == null || authorizationScopesVariableComponentKey.isEmpty()) {
            builder.recordMissingAuthorizationScopeVariableComponentKey(dataType);
        } else {
            Configuration.DataTypeDescription dataTypeDescription = configuration.getDataTypes().get(dataType);
            authorizationScopesVariableComponentKey.entrySet().stream().forEach(authorizationScopeVariableComponentKeyEntry -> {
                String authorizationScopeName = authorizationScopeVariableComponentKeyEntry.getKey();
                VariableComponentKey authorizationScopeVariableComponentKey = authorizationScopeVariableComponentKeyEntry.getValue();
                if (authorizationScopeVariableComponentKey.getVariable() == null) {
                    builder.recordAuthorizationScopeVariableComponentKeyMissingVariable(dataType, authorizationScopeName, variables);
                } else {
                    String variable = authorizationScopeVariableComponentKey.getVariable();
                    Configuration.ColumnDescription variableInDescription = dataTypeDescription.getData().get(variable);
                    if (!dataTypeDescription.getData().containsKey(variable)) {
                        builder.recordAuthorizationScopeVariableComponentKeyUnknownVariable(authorizationScopeVariableComponentKey, variables);
                    } else {
                        String component = authorizationScopeVariableComponentKey.getComponent();
                        LinkedHashMap<String, Configuration.VariableComponentDescription> componentsInDescription = variableInDescription.getComponents();
                        if (component == null) {
                            builder.recordAuthorizationVariableComponentKeyMissingComponent(dataType, authorizationScopeName, variable, componentsInDescription.keySet());
                        } else {
                            if (!componentsInDescription.containsKey(component)) {
                                builder.recordAuthorizationVariableComponentKeyUnknownComponent(authorizationScopeVariableComponentKey, componentsInDescription.keySet());
                            } else {
                                Configuration.CheckerDescription authorizationScopeVariableComponentChecker = dataTypeDescription.getData().get(variable).getComponents().get(authorizationScopeVariableComponentKey.getComponent()).getChecker();
                                if (authorizationScopeVariableComponentChecker == null || !"Reference".equals(authorizationScopeVariableComponentChecker.getName())) {
                                    builder.recordAuthorizationScopeVariableComponentWrongChecker(authorizationScopeVariableComponentKey, "Date");
                                }
                                String refType = null;
                                Configuration.CheckerConfigurationDescription checkerConfigurationDescription = authorizationScopeVariableComponentChecker.getParams();
                                if (checkerConfigurationDescription == null) {
                                    builder.recordAuthorizationScopeVariableComponentReftypeNull(authorizationScopeVariableComponentKey, configuration.getReferences().keySet());
                                } else {
                                    refType = checkerConfigurationDescription.getRefType();
                                    if (refType == null || !configuration.getReferences().containsKey(refType)) {
                                        builder.recordAuthorizationScopeVariableComponentReftypeUnknown(authorizationScopeVariableComponentKey, refType, configuration.getReferences().keySet());
                                    } else {
                                        Set<String> compositesReferences = configuration.getCompositeReferences().values().stream()
                                                .map(e -> e.getComponents())
                                                .flatMap(List::stream)
                                                .map(crd -> crd.getReference())
                                                .collect(Collectors.toSet());
                                        if (!compositesReferences.contains(refType)) {
                                            builder.recordAuthorizationVariableComponentMustReferToCompositereference(dataType, authorizationScopeName, refType, compositesReferences);
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            });
        }
    }

    private void verifyDatatypeTimeScopeExistsAndIsValid(ConfigurationParsingResult.Builder builder, String dataType, Configuration.DataTypeDescription dataTypeDescription, Set<String> variables, VariableComponentKey timeScopeVariableComponentKey) {
        if (timeScopeVariableComponentKey == null) {
            builder.recordMissingTimeScopeVariableComponentKey(dataType);
        } else {
            if (timeScopeVariableComponentKey.getVariable() == null) {
                builder.recordTimeScopeVariableComponentKeyMissingVariable(dataType, variables);
            } else {
                if (!dataTypeDescription.getData().containsKey(timeScopeVariableComponentKey.getVariable())) {
                    builder.recordTimeScopeVariableComponentKeyUnknownVariable(timeScopeVariableComponentKey, variables);
                } else {
                    if (timeScopeVariableComponentKey.getComponent() == null) {
                        builder.recordTimeVariableComponentKeyMissingComponent(dataType, timeScopeVariableComponentKey.getVariable(), dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().keySet());
                    } else {
                        if (!dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().containsKey(timeScopeVariableComponentKey.getComponent())) {
                            builder.recordTimeVariableComponentKeyUnknownComponent(timeScopeVariableComponentKey, dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().keySet());
                        } else {
                            Configuration.CheckerDescription timeScopeVariableComponentChecker = dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().get(timeScopeVariableComponentKey.getComponent()).getChecker();
                            if (timeScopeVariableComponentChecker == null || !"Date".equals(timeScopeVariableComponentChecker.getName())) {
                                builder.recordTimeScopeVariableComponentWrongChecker(timeScopeVariableComponentKey, "Date");
                            }
                            Optional.ofNullable(timeScopeVariableComponentChecker)
                                    .map(checkerDescription -> checkerDescription.getParams())
                                    .map(Configuration.CheckerConfigurationDescription::getPattern)
                                    .ifPresent(pattern -> {
                                        if (!LocalDateTimeRange.getKnownPatterns().contains(pattern)) {
                                            builder.recordTimeScopeVariableComponentPatternUnknown(timeScopeVariableComponentKey, pattern, LocalDateTimeRange.getKnownPatterns());
                                        }
                                    });
                        }
                    }
                }
            }
        }
    }

    private void verifyDatatypeCheckersExists(ConfigurationParsingResult.Builder builder, Configuration.DataTypeDescription dataTypeDescription, String dataType) {
        for (Map.Entry<String, Configuration.ColumnDescription> columnDescriptionEntry : dataTypeDescription.getData().entrySet()) {
            Configuration.ColumnDescription columnDescription = columnDescriptionEntry.getValue();
            String variable = columnDescriptionEntry.getKey();
            for (Map.Entry<String, Configuration.VariableComponentDescription> variableComponentDescriptionEntry : columnDescription.getComponents().entrySet()) {
                Configuration.VariableComponentDescription variableComponentDescription = variableComponentDescriptionEntry.getValue();
                if (variableComponentDescription == null) {
                    continue;
                }
                String component = variableComponentDescriptionEntry.getKey();
                Configuration.CheckerDescription checker = variableComponentDescription.getChecker();
                if (checker == null) {
                    continue;
                }
                ImmutableSet<String> variableComponentCheckers = ImmutableSet.of("Date", "Float", "Integer", "RegularExpression", "Reference");

                if (!variableComponentCheckers.contains(checker.getName())) {
                    builder.recordUnknownCheckerNameForVariableComponentChecker(dataType, variable, component, checker.getName(), variableComponentCheckers);
                }
            }
        }
    }

    private void verifyDatatypeCheckerGroovyExpressionExistsAndCanCompile(ConfigurationParsingResult.Builder builder, Configuration.DataTypeDescription dataTypeDescription) {
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : dataTypeDescription.getValidations().entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            String lineValidationRuleKey = validationEntry.getKey();
            Configuration.CheckerDescription checker = lineValidationRuleDescription.getChecker();
            if (GroovyLineChecker.NAME.equals(checker.getName())) {
                String expression = Optional.of(checker)
                        .map(Configuration.CheckerDescription::getParams)
                        .map(Configuration.CheckerConfigurationDescription::getGroovy)
                        .map(GroovyConfiguration::getExpression)
                        .orElse(null);
                if (StringUtils.isBlank(expression)) {
                    builder.recordMissingRequiredExpression(lineValidationRuleKey);
                } else {
                    Optional<GroovyExpression.CompilationError> compileResult = GroovyLineChecker.validateExpression(expression);
                    compileResult.ifPresent(compilationError -> builder.recordIllegalGroovyExpression(lineValidationRuleKey, expression, compilationError));
                }
            } else {
                builder.recordUnknownCheckerName(lineValidationRuleKey, checker.getName());
            }
        }
    }

    private void verifyDatatypeCheckerReferenceRefersToExistingReference(ConfigurationParsingResult.Builder builder, Set<String> references, String dataType, Configuration.DataTypeDescription dataTypeDescription) {
        for (Map.Entry<String, Configuration.ColumnDescription> dataEntry : dataTypeDescription.getData().entrySet()) {
            String datum = dataEntry.getKey();
            Configuration.ColumnDescription datumDescription = dataEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : datumDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                Configuration.VariableComponentDescription variableComponentDescription = componentEntry.getValue();
                if (variableComponentDescription != null) {
                    Configuration.CheckerDescription checkerDescription = variableComponentDescription.getChecker();
                    if ("Reference".equals(checkerDescription.getName())) {
                        if (checkerDescription.getParams() != null && checkerDescription.getParams().getRefType() != null) {
                            String refType = checkerDescription.getParams().getRefType();
                            if (!references.contains(refType)) {
                                builder.unknownReferenceForChecker(dataType, datum, component, refType, references);
                            }
                        } else {
                            builder.missingReferenceForChecker(dataType, datum, component, references);
                        }
                    }
                }
            }
        }
    }

    private void verifyReferenceKeyColumns(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        List<String> keyColumns = referenceDescription.getKeyColumns();
        if (keyColumns.isEmpty()) {
            builder.recordMissingKeyColumnsForReference(reference);
        } else {
            Set<String> columns = referenceDescription.getColumns().keySet();
            ImmutableSet<String> keyColumnsSet = ImmutableSet.copyOf(keyColumns);
            ImmutableSet<String> unknownUsedAsKeyElementColumns = Sets.difference(keyColumnsSet, columns).immutableCopy();
            if (!unknownUsedAsKeyElementColumns.isEmpty()) {
                builder.recordInvalidKeyColumns(reference, unknownUsedAsKeyElementColumns, columns);
            }
        }
    }

    private void verifyInternationalizedColumnsExistsForPattern(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        Set<String> internationalizedColumnsForDisplay = Optional.ofNullable(configuration.getInternationalization())
                .map(i -> i.getReferences())
                .map(r -> r.getOrDefault(reference, null))
                .map(im -> im.getInternationalizationDisplay())
                .map(ic -> ic.getPattern())
                .map(patterns -> patterns.values()
                        .stream()
                        .map(pattern -> InternationalizationDisplay.getPatternColumns(pattern))
                        .flatMap(List::stream)
                        .collect(Collectors.toSet())
                )
                .orElseGet(Set::of);
        Set<String> internationalizedColumns = Optional.ofNullable(configuration.getInternationalization())
                .map(i -> i.getReferences())
                .map(r -> r.getOrDefault(reference, null))
                .map(im -> im.getInternationalizedColumns())
                .map(ic -> {
                    Set<String> columns = new LinkedHashSet<>(ic.keySet());
                    ic.values().stream()
                            .forEach(v -> columns.addAll(v.values()));
                    return columns;
                })
                .orElse(new HashSet<>());
        Set<String> columns = Optional.ofNullable(referenceDescription)
                .map(r -> r.getColumns())
                .map(c -> new LinkedHashSet(c.keySet()))
                .orElseGet(LinkedHashSet::new);
        columns.addAll(internationalizedColumns);


        ImmutableSet<String> internationalizedColumnsSet = ImmutableSet.copyOf(internationalizedColumns);
        ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsForDisplay, columns).immutableCopy();
        if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
            builder.recordInvalidInternationalizedColumns(reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
        }
    }

    private void verifyInternationalizedColumnsExistsForPatternInDatatype(Configuration configuration, ConfigurationParsingResult.Builder builder, String dataType, Configuration.DataTypeDescription dataTypeDescription) {
        Map<String, InternationalizationDisplay> internationalizationDisplayMap = Optional.ofNullable(configuration.getInternationalization())
                .map(i -> i.getDataTypes())
                .map(r -> r.getOrDefault(dataType, null))
                .map(im -> im.getInternationalizationDisplay())
                .orElseGet(Map::of);
        for (Map.Entry<String, InternationalizationDisplay> internationalizationDisplayEntry : internationalizationDisplayMap.entrySet()) {
            Set<String> internationalizedColumnsForDisplay = Optional.ofNullable(internationalizationDisplayEntry.getValue())
                    .map(ic -> ic.getPattern())
                    .map(patterns -> patterns.values()
                            .stream()
                            .map(pattern -> InternationalizationDisplay.getPatternColumns(pattern))
                            .flatMap(List::stream)
                            .collect(Collectors.toSet())
                    )
                    .orElseGet(Set::of);
            String reference = internationalizationDisplayEntry.getKey();
            Map<String, Configuration.ReferenceDescription> references = Optional.ofNullable(configuration.getReferences())
                    .orElse(new LinkedHashMap<>());
            if (!references.containsKey(reference)) {
                builder.recordUnknownReferenceInDatatypeReferenceDisplay(dataType, reference, references.keySet());
                return;
            }


            Set<String> internationalizedColumns = Optional.ofNullable(configuration.getInternationalization())
                    .map(i -> i.getReferences())
                    .map(r -> r.getOrDefault(reference, null))
                    .map(im -> im.getInternationalizedColumns())
                    .map(ic -> {
                        Set<String> columns = new LinkedHashSet<>(ic.keySet());
                        ic.values().stream()
                                .forEach(v -> columns.addAll(v.values()));
                        return columns;
                    })
                    .orElse(new HashSet<>());
            Configuration.ReferenceDescription referenceDescription = configuration.getReferences().getOrDefault(reference, null);
            Set<String> columns = Optional.ofNullable(referenceDescription)
                    .map(r -> r.getColumns())
                    .map(c -> new LinkedHashSet(c.keySet()))
                    .orElseGet(LinkedHashSet::new);
            columns.addAll(internationalizedColumns);


            ImmutableSet<String> internationalizedColumnsSet = ImmutableSet.copyOf(internationalizedColumns);
            ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsForDisplay, columns).immutableCopy();
            if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
                builder.recordInvalidInternationalizedColumnsForDataType(dataType, reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
            }
        }
    }

    private void verifyInternationalizedColumnsExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        Set<String> internationalizedColumns = Optional.ofNullable(configuration.getInternationalization())
                .map(i -> i.getReferences())
                .map(r -> r.getOrDefault(reference, null))
                .map(im -> im.getInternationalizedColumns())
                .map(ic -> {
                    Set<String> columns = new LinkedHashSet<>(ic.keySet());
                    ic.values().stream()
                            .forEach(v -> columns.addAll(v.values()));
                    return columns;
                })
                .orElse(new HashSet<>());
        Set<String> columns = Optional.ofNullable(referenceDescription)
                .map(r -> r.getColumns())
                .map(c -> c.keySet())
                .orElse(new HashSet<>());

        ImmutableSet<String> internationalizedColumnsSet = ImmutableSet.copyOf(internationalizedColumns);
        ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsSet, columns).immutableCopy();
        if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
            builder.recordInvalidInternationalizedColumns(reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
        }
    }

    private void verifyValidationCheckersAreValids(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry, Set<String> references) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationRuleDescriptionEntry : referenceDescription.getValidations().entrySet()) {
            String validationRuleDescriptionEntryKey = validationRuleDescriptionEntry.getKey();
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationRuleDescriptionEntry.getValue();
            Configuration.CheckerDescription checker = lineValidationRuleDescription.getChecker();
            if (checker == null) {
                continue;
            }
            ImmutableSet<String> variableComponentCheckers = ImmutableSet.of("Date", "Float", "Integer", "RegularExpression", "Reference");
            String columns = checker.getParams().getColumns();
            Set<String> groovyColumn = Optional.ofNullable(checker)
                    .map(check->check.getParams())
                    .filter(params->params.getGroovy() != null)
                    .map(params-> MoreObjects.firstNonNull(params.getColumns(), ""))

                    // autant mettre une collection dans le YAML directement
                    .map(values-> values.split(","))
                    .map(values-> Arrays.stream(values).collect(Collectors.toSet()))
                    .orElse(Set.of());

            if (GroovyLineChecker.NAME.equals(checker.getName())) {
                String expression =Optional.of(checker)
                        .map(Configuration.CheckerDescription::getParams)
                        .map(Configuration.CheckerConfigurationDescription::getGroovy)
                        .map(GroovyConfiguration::getExpression)
                        .orElse(null);
                if (StringUtils.isBlank(expression)) {
                    builder.recordMissingRequiredExpression(validationRuleDescriptionEntryKey);
                } else {
                    Optional<GroovyExpression.CompilationError> compileResult = GroovyLineChecker.validateExpression(expression);
                    compileResult.ifPresent(compilationError -> builder.recordIllegalGroovyExpression(validationRuleDescriptionEntryKey, expression, compilationError));
                }
            } else if (variableComponentCheckers.contains(checker.getName())) {
                if (Strings.isNullOrEmpty(columns))
                    builder.missingParamColumnReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference);
                else {
                    List<String> columnsList = Stream.of(columns.split(",")).collect(Collectors.toList());
                    Set<String> referencesColumns = referenceDescription.getColumns().keySet();
                    ImmutableSet availablesColumns = new ImmutableSet.Builder<>()
                            .addAll(referencesColumns)
                            .addAll(groovyColumn)
                            .build();

                    List<String> missingColumns = columnsList.stream()
                            .filter(c -> !availablesColumns.contains(c))
                            .collect(Collectors.toList());

                    if (!missingColumns.isEmpty()) {
                        builder.missingColumnReferenceForCheckerInReference(validationRuleDescriptionEntryKey, availablesColumns, checker.getName(), missingColumns, reference);
                    }
                }
                if ("Reference".equals(checker.getName())) {
                    if (checker.getParams() != null && checker.getParams().getRefType() != null) {
                        String refType = checker.getParams().getRefType();
                        if (!references.contains(refType)) {
                            builder.unknownReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference, refType, references);
                        }
                    } else {
                        builder.missingReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference, references);
                    }
                }
            } else {
                builder.recordUnknownCheckerNameForVariableComponentCheckerInReference(validationRuleDescriptionEntryKey, reference, checker.getName(), variableComponentCheckers);
            }
        }
    }

    private ConfigurationParsingResult onJsonProcessingException(JsonProcessingException e) {
        if (log.isErrorEnabled()) {
            log.error("exception non-gérée en essayant de parser le YAML", e);
        }
        return ConfigurationParsingResult.builder()
                .recordUnableToParseYaml(e.getMessage())
                .build();
    }

    private ConfigurationParsingResult onMappingExceptions(List<IllegalArgumentException> exceptions) {
        ConfigurationParsingResult.Builder builder = ConfigurationParsingResult.builder();
        exceptions.stream()
                .forEach(exception -> {
                    if (exception.getCause() instanceof UnrecognizedPropertyException) {
                        UnrecognizedPropertyException e = (UnrecognizedPropertyException) exception.getCause();
                        int lineNumber = e.getLocation().getLineNr();
                        int columnNumber = e.getLocation().getColumnNr();
                        String unknownPropertyName = e.getPropertyName();
                        Collection<String> knownProperties = (Collection) e.getKnownPropertyIds();
                        builder.recordUnrecognizedProperty(lineNumber, columnNumber, unknownPropertyName, knownProperties);
                    } else if (exception.getCause() instanceof InvalidFormatException) {
                        InvalidFormatException e = (InvalidFormatException) exception.getCause();
                        int lineNumber = e.getLocation().getLineNr();
                        int columnNumber = e.getLocation().getColumnNr();
                        String value = e.getValue().toString();
                        String targetTypeName = e.getTargetType().getName();
                        builder.recordInvalidFormat(lineNumber, columnNumber, value, targetTypeName);
                    } else {
                        builder.unknownIllegalException(exception.getCause().getLocalizedMessage());
                    }
                });
        return builder.build();
    }

    private ConfigurationParsingResult onUnrecognizedPropertyException(UnrecognizedPropertyException e) {
        int lineNumber = e.getLocation().getLineNr();
        int columnNumber = e.getLocation().getColumnNr();
        String unknownPropertyName = e.getPropertyName();
        Collection<String> knownProperties = (Collection) e.getKnownPropertyIds();
        return ConfigurationParsingResult.builder()
                .recordUnrecognizedProperty(lineNumber, columnNumber, unknownPropertyName, knownProperties)
                .build();
    }

    private ConfigurationParsingResult onInvalidFormatException(InvalidFormatException e) {
        int lineNumber = e.getLocation().getLineNr();
        int columnNumber = e.getLocation().getColumnNr();
        String value = e.getValue().toString();
        String targetTypeName = e.getTargetType().getName();
        return ConfigurationParsingResult.builder()
                .recordInvalidFormat(lineNumber, columnNumber, value, targetTypeName)
                .build();
    }

    @Getter
    @Setter
    @ToString
    private static class Versioned {
        int version;
    }

}
