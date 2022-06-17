package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import fr.inra.oresing.checker.*;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.*;
import fr.inra.oresing.model.internationalization.InternationalizationDataTypeMap;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import fr.inra.oresing.model.internationalization.InternationalizationReferenceMap;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ApplicationConfigurationService {

    public static final String OTHERS_DATAGROUPS = "_others_";
    private static final ImmutableSet<CheckerType> CHECKER_ON_TARGET_NAMES =
            ImmutableSet.of(CheckerType.Date, CheckerType.Integer, CheckerType.Float, CheckerType.RegularExpression, CheckerType.Reference);
    private static final ImmutableSet<CheckerType> ALL_CHECKER_NAMES = ImmutableSet.<CheckerType>builder()
            .addAll(CHECKER_ON_TARGET_NAMES)
            .add(CheckerType.GroovyExpression)
            .build();

    ConfigurationParsingResult unzipConfiguration(MultipartFile file) {
        return null;
    }

    ConfigurationParsingResult parseConfigurationBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return ConfigurationParsingResult.builder()
                    .emptyFile()
                    .build();
        }
        try {
            YAMLMapper mapper = new YAMLMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Versioned versioned = mapper.readValue(bytes, Versioned.class);
            int actualVersion = versioned.getVersion();
            int expectedVersion = 1;
            if (actualVersion != expectedVersion) {
                return ConfigurationParsingResult.builder()
                        .unsupportedVersion(actualVersion, expectedVersion)
                        .build();
            }
        } catch (UnrecognizedPropertyException e) {
            return onUnrecognizedPropertyException(e);
        } catch (InvalidFormatException e) {
            return onInvalidFormatException(e);
        } catch (JsonProcessingException e) {
            return onJsonProcessingException(e);
        } catch (IOException e) {
            throw new SiOreIllegalArgumentException(
                    "IOException",
                    Map.of(
                            "message", e.getLocalizedMessage()
                    )
            );
            // throw new OreSiTechnicalException("ne peut lire le fichier YAML", e);
        }

        Configuration configuration;
        try {
            YAMLMapper mapper = new YAMLMapper();
            configuration = mapper.readValue(bytes, Configuration.class);

        } catch (UnrecognizedPropertyException e) {
            return onUnrecognizedPropertyException(e);
        } catch (InvalidFormatException e) {
            return onInvalidFormatException(e);
        } catch (JsonProcessingException e) {
            return onJsonProcessingException(e);
        } catch (IOException e) {
            throw new SiOreIllegalArgumentException(
                    "IOException",
                    Map.of(
                            "message", e.getLocalizedMessage()
                    )
            );
            // throw new OreSiTechnicalException("ne peut lire le fichier YAML", e);
        }
        return getConfigurationParsingResultForSyntacticallyValidYaml(configuration);
    }

    private ConfigurationParsingResult getConfigurationParsingResultForSyntacticallyValidYaml(Configuration configuration) {
        ConfigurationParsingResult.Builder builder = ConfigurationParsingResult.builder();
        Set<String> references = configuration.getReferences().keySet();
        ImmutableSet.Builder<String> requiredAuthorizationsAttributesBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.CompositeReferenceDescription> compositeReferenceEntry : configuration.getCompositeReferences().entrySet()) {
            verifyCompositeReferenceReferenceExists(configuration, builder, compositeReferenceEntry);
            verifyCompositeReferenceParentRecursiveColumnExists(configuration, builder, compositeReferenceEntry);
        }

        for (Map.Entry<String, Configuration.ReferenceDescription> referenceEntry : configuration.getReferences().entrySet()) {
            verifyReferenceKeyColumns(builder, referenceEntry);
            verifyInternationalizedColumnsExists(configuration, builder, referenceEntry);
            verifyInternationalizedColumnsExistsForPattern(configuration, builder, referenceEntry);
            verifyReferenceColumnsDeclarations(builder, referenceEntry, references);
            verifyReferenceValidationRules(builder, referenceEntry, references);
        }

        for (Map.Entry<String, Configuration.DataTypeDescription> entry : configuration.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            verifyDataTypeVariableComponentDeclarations(builder, references, dataType, dataTypeDescription);
            verifyDataTypeValidationRules(builder, dataType, dataTypeDescription, references);
            verifyInternationalizedColumnsExistsForPatternInDatatype(configuration, builder, dataType);
            verifyUniquenessComponentKeysInDatatype(dataType, dataTypeDescription, builder);
            verifyDatatypeRepository(dataType, dataTypeDescription, builder);

            Configuration.AuthorizationDescription authorization = dataTypeDescription.getAuthorization();
            Set<String> variables = dataTypeDescription.getData().keySet();

            if (authorization != null) {
                //builder.missingAuthorizationForDatatype(dataType);
//                authorization = Configuration.AuthorizationDescription.DEFAULT_INSTANCE;
//                dataTypeDescription.setAuthorization(authorization);
                /*} else {*/
                VariableComponentKey timeScopeVariableComponentKey = authorization.getTimeScope();
                verifyDatatypeTimeScopeExistsAndIsValid(builder, dataType, dataTypeDescription, variables, timeScopeVariableComponentKey);

                LinkedHashMap<String, Configuration.AuthorizationScopeDescription> authorizationScopesVariableComponentKey = authorization.getAuthorizationScopes();
                verifyDatatypeAuthorizationScopeExistsAndIsValid(builder, dataType, configuration, variables, authorizationScopesVariableComponentKey);
                requiredAuthorizationsAttributesBuilder.addAll(authorizationScopesVariableComponentKey.keySet());

                Multiset<String> variableOccurrencesInDataGroups = TreeMultiset.create();
                verifyDatatypeDataGroupsContainsExistingVariables(builder, dataTypeDescription, variables, variableOccurrencesInDataGroups);

                verifyDatatypeBindingToExistingVariableComponent(builder, variables, variableOccurrencesInDataGroups);
                verifyDatatypeBindingToExistingVariableComponent(builder, dataTypeDescription, dataType, variables);
                verifyChartDescription(builder, dataType, dataTypeDescription);
            }
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
                        final Map<String, Configuration.VariableComponentDescription> components = entry.getValue().doGetAllComponentDescriptions();
                        if (Strings.isNullOrEmpty(valueComponent)) {
                            builder.unDeclaredValueForChart(datatype, variable, components.keySet());
                        } else {
                            if (!components.containsKey(valueComponent)) {
                                builder.missingValueComponentForChart(datatype, variable, valueComponent, components.keySet());
                            }
                            final VariableComponentKey aggregation = chartDescription.getAggregation();
                            if (aggregation != null) {
                                if (!dataTypeDescription.getData().containsKey(aggregation.getVariable())) {
                                    builder.missingAggregationVariableForChart(datatype, variable, aggregation, dataTypeDescription.getData().keySet());
                                } else if (!dataTypeDescription.getData().get(aggregation.getVariable()).hasComponent(aggregation.getComponent())) {
                                    builder.missingAggregationComponentForChart(datatype, variable, aggregation, components.keySet());
                                }

                            }
                            final String standardDeviation = chartDescription.getStandardDeviation();
                            if (standardDeviation != null && !components.containsKey(standardDeviation)) {
                                builder.missingStandardDeviationComponentForChart(datatype, variable, standardDeviation, components.keySet());
                            }
                            final String unit = chartDescription.getUnit();
                            if (standardDeviation != null && !components.containsKey(unit)) {
                                builder.missingUnitComponentForChart(datatype, variable, unit, components.keySet());
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
                .map(Configuration.CompositeReferenceComponentDescription::getReference)
                .filter(ref -> {
                    if (ref == null) {
                        builder.missingReferenceInCompositereference(compositeReferenceName);
                    }
                    return ref != null;
                })
                .collect(Collectors.toSet());
        Set<String> existingReferences = configuration.getReferences().keySet();
        ImmutableSet<String> unknownReferences = Sets.difference(expectingReferences, existingReferences).immutableCopy();
        if (!unknownReferences.isEmpty()) {
            builder.unknownReferenceInCompositeReference(compositeReferenceName, unknownReferences, existingReferences);
        } else {
            verifyCompositeReferenceParentColumnExists(configuration, builder, compositeReferenceEntry);
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
                builder.requiredReferenceInCompositeReferenceForParentKeyColumn(compositeReferenceName, parentKeyColumn);
            } else if (previousReference != null) {
                String reference = component.getReference();
                if (parentKeyColumn == null) {
                    builder.requiredParentKeyColumnInCompositeReferenceForReference(compositeReferenceName, reference, previousReference);
                } else if (!configuration.getReferences().get(reference).hasStaticColumn(parentKeyColumn)) {
                    builder.missingParentColumnForReferenceInCompositeReference(compositeReferenceName, reference, parentKeyColumn);
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
            if (parentRecursiveKey != null && !configuration.getReferences().get(reference).hasStaticColumn(parentRecursiveKey)) {
                builder.missingParentRecursiveKeyColumnForReferenceInCompositeReference(compositeReferenceName, reference, parentRecursiveKey);
            }
        }
    }

    private void verifyDatatypeBindingToExistingVariableComponent(ConfigurationParsingResult.Builder builder, Configuration.DataTypeDescription dataTypeDescription, String dataType, Set<String> variables) {

        final Configuration.FormatDescription format = dataTypeDescription.getFormat();
        verifyFormatDescriptionIsValid(builder, format, dataType);
        for (Configuration.ColumnBindingDescription columnBindingDescription : format.getColumns()) {
            VariableComponentKey boundTo = columnBindingDescription.getBoundTo();
            String variable = boundTo.getVariable();
            if (variables.contains(variable)) {
                String component = boundTo.getComponent();
                Set<String> components = dataTypeDescription.getData().get(variable).getComponents().keySet();
                if (components.contains(component)) {
                    // OK
                } else {
                    builder.csvBoundToUnknownVariableComponent(columnBindingDescription.getHeader(), variable, component, components);
                }
            } else {
                builder.csvBoundToUnknownVariable(columnBindingDescription.getHeader(), variable, variables);
            }
        }
    }

    private void verifyFormatDescriptionIsValid(ConfigurationParsingResult.Builder builder, Configuration.FormatDescription format, String dataType) {
        format.getConstants()
                .forEach(headerConstantDescription -> {
                    final int columnNumber = headerConstantDescription.getColumnNumber();
                    final String headerName = headerConstantDescription.getHeaderName();
                    final int rowNumber = headerConstantDescription.getRowNumber();
                    final int headerLine = format.getHeaderLine();
                    if (rowNumber == headerLine) {
                        builder.sameHeaderLineAndFirstRowLineForConstantDescription(dataType);
                    }
                    final int firstRowLine = format.getFirstRowLine();
                    if (rowNumber >= firstRowLine) {
                        builder.tooBigRowLineForConstantDescription(dataType);
                    }
                    if (rowNumber < 1) {
                        builder.tooLittleRowLineForConstantDescription(dataType);
                    }
                    if (rowNumber < headerLine && rowNumber < 1) {
                        builder.missingRowLineForConstantDescription(dataType);
                    } else if (rowNumber > headerLine && columnNumber < 1 && headerName == null) {
                        builder.missingColumnNumberOrHeaderNameForConstantDescription(dataType);
                    } else {
                        final VariableComponentKey boundTo = headerConstantDescription.getBoundTo();
                        if (boundTo == null) {
                            builder.missingBoundToForConstantDescription(dataType);
                        } else if (headerConstantDescription.getExportHeader() == null) {
                            builder.missingExportHeaderNameForConstantDescription(dataType);
                        }
                    }
                });
    }

    private void verifyDatatypeBindingToExistingVariableComponent(ConfigurationParsingResult.Builder builder, Set<String> variables, Multiset<String> variableOccurrencesInDataGroups) {
        variables.forEach(variable -> {
            int count = variableOccurrencesInDataGroups.count(variable);
            if (count == 0) {
                builder.undeclaredDataGroupForVariable(variable);
            } else if (count > 1) {
                builder.variableInMultipleDataGroup(variable);
            }
        });
    }

    private void verifyDatatypeDataGroupsContainsExistingVariables(ConfigurationParsingResult.Builder builder, Configuration.DataTypeDescription dataTypeDescription, Set<String> variables, Multiset<String> variableOccurrencesInDataGroups) {
        final LinkedHashMap<String, Configuration.DataGroupDescription> dataGroups = dataTypeDescription.getAuthorization().getDataGroups();
        if (dataGroups.isEmpty()) {
            final Configuration.DataGroupDescription dataGroupDescription = new Configuration.DataGroupDescription();
            for (String variable : variables) {
                dataGroupDescription.getData().add(variable);
            }
            dataGroups.put(OTHERS_DATAGROUPS, dataGroupDescription);
            variableOccurrencesInDataGroups.addAll(variables);
            return;
        }
        for (Map.Entry<String, Configuration.DataGroupDescription> dataGroupEntry : dataGroups.entrySet()) {
            String dataGroup = dataGroupEntry.getKey();
            Configuration.DataGroupDescription dataGroupDescription = dataGroupEntry.getValue();
            Set<String> dataGroupVariables = dataGroupDescription.getData();
            variableOccurrencesInDataGroups.addAll(dataGroupVariables);
            ImmutableSet<String> unknownVariables = Sets.difference(dataGroupVariables, variables).immutableCopy();
            if (!unknownVariables.isEmpty()) {
                builder.unknownVariablesInDataGroup(dataGroup, unknownVariables, variables);
            }
        }
    }

    private void verifyDatatypeAuthorizationScopeExistsAndIsValid(ConfigurationParsingResult.Builder builder, String dataType, Configuration configuration, Set<String> variables, LinkedHashMap<String, Configuration.AuthorizationScopeDescription> authorizationScopesVariableComponentKey) {
        if (authorizationScopesVariableComponentKey == null || authorizationScopesVariableComponentKey.isEmpty()) {
/*
            on ne revoit pas d'erreur, mais les authorisations ne seront pas posées sur un localizationscope
            builder.missingAuthorizationScopeVariableComponentKey(dataType);
*/
        } else {
            Configuration.DataTypeDescription dataTypeDescription = configuration.getDataTypes().get(dataType);
            authorizationScopesVariableComponentKey.entrySet().stream().forEach(authorizationScopeVariableComponentKeyEntry -> {
                String authorizationScopeName = authorizationScopeVariableComponentKeyEntry.getKey();
                Configuration.AuthorizationScopeDescription authorizationScopeDescription = authorizationScopeVariableComponentKeyEntry.getValue();
                VariableComponentKey authorizationScopeVariableComponentKey = authorizationScopeDescription.getVariableComponentKey();
                if (authorizationScopeVariableComponentKey.getVariable() == null) {
                    builder.authorizationScopeVariableComponentKeyMissingVariable(dataType, authorizationScopeName, variables);
                } else {
                    String variable = authorizationScopeVariableComponentKey.getVariable();
                    Configuration.VariableDescription variableInDescription = dataTypeDescription.getData().get(variable);
                    if (!dataTypeDescription.getData().containsKey(variable)) {
                        builder.authorizationScopeVariableComponentKeyUnknownVariable(authorizationScopeVariableComponentKey, variables);
                    } else {
                        String component = authorizationScopeVariableComponentKey.getComponent();
                        Map<String, Configuration.VariableComponentDescription> componentsInDescription = variableInDescription.doGetAllComponentDescriptions();
                        if (component == null) {
                            builder.authorizationVariableComponentKeyMissingComponent(dataType, authorizationScopeName, variable, componentsInDescription.keySet());
                        } else {
                            if (!componentsInDescription.containsKey(component)) {
                                builder.authorizationVariableComponentKeyUnknownComponent(authorizationScopeVariableComponentKey, componentsInDescription.keySet());
                            } else {
                                final Map<String, Configuration.VariableComponentDescription> allComponentDescriptions = dataTypeDescription.getData().get(variable).doGetAllComponentDescriptions();
                                if (allComponentDescriptions.get(authorizationScopeVariableComponentKey.getComponent()) == null) {
                                    builder.authorizationScopeMissingReferenceCheckerForAuthorizationScope(authorizationScopeVariableComponentKeyEntry, dataType);
                                } else {
                                    Configuration.CheckerDescription authorizationScopeVariableComponentChecker = allComponentDescriptions.get(authorizationScopeVariableComponentKey.getComponent()).getChecker();
                                    if (authorizationScopeVariableComponentChecker == null || !CheckerType.Reference.equals(authorizationScopeVariableComponentChecker.getName())) {
                                        builder.authorizationScopeVariableComponentWrongChecker(authorizationScopeVariableComponentKey, "Date");
                                    }
                                    String refType;
                                    Configuration.CheckerConfigurationDescription checkerConfigurationDescription = null;
                                    if (authorizationScopeVariableComponentChecker != null) {
                                        checkerConfigurationDescription = authorizationScopeVariableComponentChecker.getParams();
                                    }
                                    if (checkerConfigurationDescription == null) {
                                        builder.authorizationScopeVariableComponentReftypeNull(authorizationScopeVariableComponentKey, configuration.getReferences().keySet());
                                    } else {
                                        refType = checkerConfigurationDescription.getRefType();
                                        if (!configuration.getReferences().containsKey(refType)) {
                                            if (refType == null) {
                                                builder.authorizationScopeVariableComponentReftypeNull(authorizationScopeVariableComponentKey, configuration.getReferences().keySet());
                                            } else {
                                                builder.authorizationScopeVariableComponentReftypeUnknown(authorizationScopeVariableComponentKey, refType, configuration.getReferences().keySet());
                                            }
                                        } else {
                                            final LinkedHashMap<String, Configuration.CompositeReferenceDescription> compositeReferences = configuration.getCompositeReferences();
                                            Set<String> compositesReferences = compositeReferences.values().stream()
                                                    .map(Configuration.CompositeReferenceDescription::getComponents)
                                                    .flatMap(List::stream)
                                                    .map(Configuration.CompositeReferenceComponentDescription::getReference)
                                                    .collect(Collectors.toSet());
                                            if (!compositesReferences.contains(refType)) {
                                                String key = String.format("default_%d", compositeReferences.keySet().stream()
                                                        .filter(k -> k.startsWith("default_"))
                                                        .count());
                                                final Configuration.CompositeReferenceDescription compositeReferenceDescription = new Configuration.CompositeReferenceDescription();
                                                final Configuration.CompositeReferenceComponentDescription compositeReferenceComponentDescription = new Configuration.CompositeReferenceComponentDescription();
                                                compositeReferenceComponentDescription.setReference(refType);
                                                compositeReferenceDescription.setComponents(List.of(compositeReferenceComponentDescription));
                                                compositeReferences.put(key, compositeReferenceDescription);
                                                //builder.authorizationScopeVariableComponentReftypeUnknown(dataType, authorizationScopeName, refType, compositesReferences);
                                            }
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
           /* on ne renvoie pas d'erreur
           builder.missingTimeScopeVariableComponentKey(dataType);*/
        } else {
            if (timeScopeVariableComponentKey.getVariable() == null) {
                builder.timeScopeVariableComponentKeyMissingVariable(dataType, variables);
            } else {
                if (!dataTypeDescription.getData().containsKey(timeScopeVariableComponentKey.getVariable())) {
                    builder.timeScopeVariableComponentKeyUnknownVariable(timeScopeVariableComponentKey, variables);
                } else {
                    if (timeScopeVariableComponentKey.getComponent() == null) {
                        builder.timeVariableComponentKeyMissingComponent(dataType, timeScopeVariableComponentKey.getVariable(), dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).doGetAllComponents());
                    } else {
                        if (!dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).hasComponent(timeScopeVariableComponentKey.getComponent())) {
                            builder.timeVariableComponentKeyUnknownComponent(timeScopeVariableComponentKey, dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).doGetAllComponents());
                        } else {
                            Configuration.CheckerDescription timeScopeVariableComponentChecker = dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).doGetAllComponentDescriptions().get(timeScopeVariableComponentKey.getComponent()).getChecker();
                            if (timeScopeVariableComponentChecker == null || !CheckerType.Date.equals(timeScopeVariableComponentChecker.getName())) {
                                builder.timeScopeVariableComponentWrongChecker(timeScopeVariableComponentKey, "Date");
                            }
                            Optional.ofNullable(timeScopeVariableComponentChecker)
                                    .map(Configuration.CheckerDescription::getParams)
                                    .map(Configuration.CheckerConfigurationDescription::getPattern)
                                    .ifPresent(pattern -> {
                                        if (!LocalDateTimeRange.getKnownPatterns().contains(pattern)) {
                                            builder.timeScopeVariableComponentPatternUnknown(timeScopeVariableComponentKey, pattern, LocalDateTimeRange.getKnownPatterns());
                                        }
                                    });
                        }
                    }
                }
            }
        }
    }

    private void verifyDataTypeValidationRules(ConfigurationParsingResult.Builder builder, final String dataType, Configuration.DataTypeDescription dataTypeDescription, final Set<String> references) {
        LineValidationRuleDescriptionValidationContext lineValidationRuleDescriptionValidationContext = new LineValidationRuleDescriptionValidationContext() {

            @Override
            public Set<String> getReferenceCheckerRefTypeParameterValidValues() {
                return references;
            }

            @Override
            public Set<CheckerTarget> getAcceptableCheckerTargets() {
                return ImmutableSet.copyOf(dataTypeDescription.doGetAllVariableComponents());
            }

            @Override
            public void unknownCheckerNameForVariableComponentChecker(String validationRuleDescriptionEntryKey, CheckerType checkerName, ImmutableSet<CheckerType> checkerOnTargetNames) {
                builder.unknownCheckerNameForVariableComponentCheckerInDataType(validationRuleDescriptionEntryKey, dataType, checkerName, checkerOnTargetNames);
            }

            @Override
            public void unknownReferenceForChecker(String validationRuleDescriptionEntryKey, String refType, Set<String> references) {
                builder.unknownReferenceForCheckerInDataType(validationRuleDescriptionEntryKey, dataType, refType, references);
            }

            @Override
            public void missingReferenceForChecker(String validationRuleDescriptionEntryKey, Set<String> references) {
                builder.missingReferenceForCheckerInDataType(validationRuleDescriptionEntryKey, dataType, references);
            }

            @Override
            public void missingRequiredExpression(String validationRuleDescriptionEntryKey) {
                builder.missingRequiredExpressionForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType);
            }

            @Override
            public void illegalGroovyExpression(String validationRuleDescriptionEntryKey, String expression, GroovyExpression.CompilationError compilationError) {
                builder.illegalGroovyExpressionForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType, expression, compilationError);
            }

            @Override
            public void missingParamColumnReferenceForChecker(String validationRuleDescriptionEntryKey) {
                builder.missingParamColumnReferenceForCheckerInDataType(validationRuleDescriptionEntryKey, dataType);
            }

            @Override
            public void missingColumnReferenceForChecker(String validationRuleDescriptionEntryKey, CheckerType checkerName, Set<CheckerTarget> knownColumns, ImmutableSet<CheckerTarget> missingColumns) {
                builder.missingColumnReferenceForCheckerInDataType(
                        validationRuleDescriptionEntryKey,
                        knownColumns.stream().map(CheckerTarget::toHumanReadableString).collect(ImmutableSet.toImmutableSet()),
                        checkerName,
                        missingColumns.stream().map(CheckerTarget::toHumanReadableString).collect(ImmutableSet.toImmutableSet()),
                        dataType);
            }

            @Override
            public void unknownCheckerNameForValidationRule(String validationRuleDescriptionEntryKey, CheckerType checkerName, ImmutableSet<CheckerType> allCheckerNames) {
                builder.unknownCheckerNameForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType, checkerName, allCheckerNames);
            }

            @Override
            public void invalidPatternForDateChecker(String validationRuleDescriptionEntryKey, String pattern) {
                builder.invalidPatternForDateCheckerForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType, pattern);
            }

            @Override
            public void invalidDurationForDateChecker(String validationRuleDescriptionEntryKey, String duration) {
                builder.invalidDurationForDateCheckerForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType, duration);
            }

            @Override
            public void invalidPatternForRegularExpressionChecker(String validationRuleDescriptionEntryKey, String pattern) {
                builder.invalidPatternForRegularExpressionCheckerForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType, pattern);
            }

            @Override
            public void illegalCheckerConfigurationParameter(String validationRuleDescriptionEntryKey, CheckerType checkerName, String parameterName) {
                builder.illegalCheckerConfigurationParameterForValidationRuleInDataType(validationRuleDescriptionEntryKey, dataType, checkerName, parameterName);
            }
        };
        for (Map.Entry<String, Configuration.LineValidationRuleWithVariableComponentsDescription> validationRuleDescriptionEntry : dataTypeDescription.getValidations().entrySet()) {
            String validationRuleDescriptionEntryKey = validationRuleDescriptionEntry.getKey();
            Configuration.LineValidationRuleWithVariableComponentsDescription lineValidationRuleDescription = validationRuleDescriptionEntry.getValue();
            verifyLineValidationRuleDescription(lineValidationRuleDescriptionValidationContext, validationRuleDescriptionEntryKey, lineValidationRuleDescription);
        }
    }

    private void verifyDataTypeVariableComponentDeclarations(ConfigurationParsingResult.Builder builder, Set<String> references, String dataType, Configuration.DataTypeDescription dataTypeDescription) {
        for (Map.Entry<String, Configuration.VariableDescription> dataEntry : dataTypeDescription.getData().entrySet()) {
            String datum = dataEntry.getKey();
            Configuration.VariableDescription datumDescription = dataEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : datumDescription.doGetAllComponentDescriptions().entrySet()) {
                String component = componentEntry.getKey();
                Configuration.VariableComponentDescription variableComponentDescription = componentEntry.getValue();
                if (variableComponentDescription != null) {
                    Configuration.CheckerDescription checkerDescription = variableComponentDescription.getChecker();
                    if (checkerDescription != null) {
                        CheckerOnOneTargetValidationContext validationContext = new CheckerOnOneTargetValidationContext() {
                            @Override
                            public Set<String> getReferenceCheckerRefTypeParameterValidValues() {
                                return references;
                            }

                            @Override
                            public void unknownReferenceForChecker(String refType, Set<String> references) {
                                builder.unknownReferenceForChecker(dataType, datum, component, refType, references);
                            }

                            @Override
                            public void missingReferenceForChecker(Set<String> references) {
                                builder.missingReferenceForChecker(dataType, datum, component, references);
                            }

                            @Override
                            public void unknownCheckerOnOneTargetName(CheckerType checkerName, ImmutableSet<CheckerType> knownCheckerNames) {
                                builder.unknownCheckerNameForVariableComponent(dataType, datum, component, checkerName, knownCheckerNames);
                            }

                            @Override
                            public void invalidPatternForDateChecker(String pattern) {
                                builder.invalidPatternForVariableComponentDateChecker(dataType, datum, component, pattern);
                            }

                            @Override
                            public void invalidDurationForDateChecker(String duration) {
                                builder.invalidDurationForVariableComponentDateChecker(dataType, datum, component, duration);
                            }

                            @Override
                            public void invalidPatternForRegularExpressionChecker(String pattern) {
                                builder.invalidPatternForVariableComponentRegularExpressionChecker(dataType, datum, component, pattern);
                            }

                            @Override
                            public void illegalCheckerConfigurationParameter(CheckerType checkerName, String parameterName) {
                                builder.illegalCheckerConfigurationParameterForVariableComponentChecker(dataType, datum, component, checkerName, parameterName);
                            }
                        };
                        verifyCheckerOnOneTarget(validationContext, checkerDescription);
                    }
                }
            }
        }
    }

    private void verifyReferenceColumnsDeclarations(ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry, Set<String> references) {
        String referenceToValidate = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        for (Map.Entry<String, Configuration.ReferenceStaticColumnDescription> columnEntry : referenceDescription.doGetStaticColumnDescriptions().entrySet()) {
            String column = columnEntry.getKey();
            Configuration.ReferenceStaticColumnDescription referenceStaticColumnDescription = columnEntry.getValue();
            if (referenceStaticColumnDescription != null) {
                Configuration.CheckerDescription checkerDescription = referenceStaticColumnDescription.getChecker();
                if (checkerDescription != null) {
                    CheckerOnOneTargetValidationContext validationContext = new CheckerOnOneTargetValidationContext() {
                        @Override
                        public Set<String> getReferenceCheckerRefTypeParameterValidValues() {
                            return references;
                        }

                        @Override
                        public void unknownReferenceForChecker(String refType, Set<String> knownReferences) {
                            builder.unknownReferenceForCheckerInReferenceColumn(referenceToValidate, column, refType, knownReferences);
                        }

                        @Override
                        public void missingReferenceForChecker(Set<String> knownReferences) {
                            builder.missingReferenceForCheckerInReferenceColumn(referenceToValidate, column, knownReferences);
                        }

                        @Override
                        public void unknownCheckerOnOneTargetName(CheckerType checkerName, ImmutableSet<CheckerType> knownCheckerNames) {
                            builder.unknownCheckerNameInReferenceColumn(referenceToValidate, column, checkerName, knownCheckerNames);
                        }

                        @Override
                        public void invalidPatternForDateChecker(String pattern) {
                            builder.invalidPatternForReferenceColumnDateChecker(referenceToValidate, column, pattern);
                        }

                        @Override
                        public void invalidDurationForDateChecker(String duration) {
                            builder.invalidDurationForReferenceColumnDateChecker(referenceToValidate, column, duration);
                        }

                        @Override
                        public void invalidPatternForRegularExpressionChecker(String pattern) {
                            builder.invalidPatternForReferenceColumnRegularExpressionChecker(referenceToValidate, column, pattern);
                        }

                        @Override
                        public void illegalCheckerConfigurationParameter(CheckerType checkerName, String parameterName) {
                            builder.illegalCheckerConfigurationParameterForReferenceColumnChecker(referenceToValidate, column, checkerName, parameterName);
                        }
                    };
                    verifyCheckerOnOneTarget(validationContext, checkerDescription);
                }
            }
        }
    }

    private void verifyCheckerOnOneTarget(CheckerOnOneTargetValidationContext builder, Configuration.CheckerDescription checkerDescription) {
        CheckerType checkerName = checkerDescription.getName();
        if (CheckerType.Reference.equals(checkerName)) {
            if (checkerDescription.getParams() != null && checkerDescription.getParams().getRefType() != null) {
                String refType = checkerDescription.getParams().getRefType();
                if (!builder.getReferenceCheckerRefTypeParameterValidValues().contains(refType)) {
                    builder.unknownReferenceForChecker(refType, builder.getReferenceCheckerRefTypeParameterValidValues());
                }
            } else {
                builder.missingReferenceForChecker(builder.getReferenceCheckerRefTypeParameterValidValues());
            }
            verifyCheckerDescriptionParameters(builder, checkerDescription, Set.of("refType"));
        } else if (CheckerType.Date.equals(checkerName)) {
            String datePattern = checkerDescription.getParams().getPattern();
            if (DateLineChecker.isValidPattern(datePattern)) {
                String duration = checkerDescription.getParams().getDuration();
                if (StringUtils.isBlank(duration)) {
                    // OK, champs facultatif
                } else if (!Duration.isValid(duration)) {
                    builder.invalidDurationForDateChecker(duration);
                }
            } else {
                builder.invalidPatternForDateChecker(datePattern);
            }
            verifyCheckerDescriptionParameters(builder, checkerDescription, Set.of("pattern", "duration"));
        } else if (CheckerType.RegularExpression.equals(checkerName)) {
            String regularExpressionPattern = checkerDescription.getParams().getPattern();
            if (!RegularExpressionChecker.isValid(regularExpressionPattern)) {
                builder.invalidPatternForRegularExpressionChecker(regularExpressionPattern);
            }
            verifyCheckerDescriptionParameters(builder, checkerDescription, Set.of("pattern"));
        } else if (Set.of(CheckerType.Integer, CheckerType.Float).contains(checkerName)) {
            verifyCheckerDescriptionParameters(builder, checkerDescription, Set.of());
        } else {
            builder.unknownCheckerOnOneTargetName(checkerName, CHECKER_ON_TARGET_NAMES);
        }
    }

    private void verifyCheckerDescriptionParameters(CheckerOnOneTargetValidationContext builder, Configuration.CheckerDescription checkerDescription, Set<String> allowedParameterNames) {
        Configuration.CheckerConfigurationDescription params = checkerDescription.getParams();
        if (params != null) {
            if (!allowedParameterNames.contains("groovy") && params.getGroovy() != null) {
                builder.illegalCheckerConfigurationParameter(checkerDescription.getName(), "groovy");
            }
            if (!allowedParameterNames.contains("pattern") && params.getPattern() != null) {
                builder.illegalCheckerConfigurationParameter(checkerDescription.getName(), "pattern");
            }
            if (!allowedParameterNames.contains("refType") && params.getRefType() != null) {
                builder.illegalCheckerConfigurationParameter(checkerDescription.getName(), "refType");
            }
            if (!allowedParameterNames.contains("duration") && params.getDuration() != null) {
                builder.illegalCheckerConfigurationParameter(checkerDescription.getName(), "duration");
            }
        }
    }

    private void verifyReferenceKeyColumns(ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        List<String> keyColumns = referenceDescription.getKeyColumns();
        if (keyColumns.isEmpty()) {
            builder.missingKeyColumnsForReference(reference);
        } else {
            Set<String> columns = referenceDescription.doGetStaticColumns();
            ImmutableSet<String> keyColumnsSet = ImmutableSet.copyOf(keyColumns);
            ImmutableSet<String> unknownUsedAsKeyElementColumns = Sets.difference(keyColumnsSet, columns).immutableCopy();
            if (!unknownUsedAsKeyElementColumns.isEmpty()) {
                builder.invalidKeyColumns(reference, unknownUsedAsKeyElementColumns, columns);
            }
        }
    }

    private void verifyInternationalizedColumnsExistsForPattern(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        Set<String> internationalizedColumnsForDisplay = Set.of();
        InternationalizationMap internationalization = configuration.getInternationalization();
        if (internationalization != null) {
            Map<String, fr.inra.oresing.model.internationalization.InternationalizationReferenceMap> references = internationalization.getReferences();
            if (references != null) {
                fr.inra.oresing.model.internationalization.InternationalizationReferenceMap internationalizationReferenceMap = references.getOrDefault(reference, null);
                if (internationalizationReferenceMap != null) {
                    InternationalizationDisplay internationalizationDisplay = internationalizationReferenceMap.getInternationalizationDisplay();
                    if (internationalizationDisplay != null) {
                        Map<Locale, String> patterns = internationalizationDisplay.getPattern();
                        if (patterns != null) {
                            internationalizedColumnsForDisplay = patterns.values()
                                    .stream()
                                    .map(InternationalizationDisplay::getPatternColumns)
                                    .flatMap(List::stream)
                                    .collect(Collectors.toSet());
                        }
                    }
                }
            }
        }
        Set<String> internationalizedColumns = getInternationalizedColumns(configuration, reference);
        Set<String> columns = Optional.ofNullable(referenceDescription)
                .map(Configuration.ReferenceDescription::doGetStaticColumnDescriptions)
                .map(c -> new LinkedHashSet(c.keySet()))
                .orElseGet(LinkedHashSet::new);
        columns.addAll(internationalizedColumns);


        ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsForDisplay, columns).immutableCopy();
        if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
            builder.invalidInternationalizedColumns(reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
        }
    }

    private Set<String> getInternationalizedColumns(Configuration configuration, String reference) {
        return Optional.ofNullable(configuration.getInternationalization())
                .map(InternationalizationMap::getReferences)
                .map(r -> r.getOrDefault(reference, null))
                .map(InternationalizationReferenceMap::getInternationalizedColumns)
                .map(ic -> {
                    Set<String> columns = new LinkedHashSet<>(ic.keySet());
                    ic.values()
                            .forEach(v -> columns.addAll(v.values()));
                    return columns;
                })
                .orElse(new HashSet<>());
    }

    private void verifyInternationalizedColumnsExistsForPatternInDatatype(Configuration configuration, ConfigurationParsingResult.Builder builder, String dataType) {
        Map<String, InternationalizationDisplay> internationalizationDisplayMap = Optional.ofNullable(configuration.getInternationalization())
                .map(InternationalizationMap::getDataTypes)
                .map(r -> r.getOrDefault(dataType, null))
                .map(InternationalizationDataTypeMap::getInternationalizationDisplay)
                .orElseGet(Map::of);
        for (Map.Entry<String, InternationalizationDisplay> internationalizationDisplayEntry : internationalizationDisplayMap.entrySet()) {
            Set<String> internationalizedColumnsForDisplay = Optional.ofNullable(internationalizationDisplayEntry.getValue())
                    .map(InternationalizationDisplay::getPattern)
                    .map(patterns -> patterns.values()
                            .stream()
                            .map(InternationalizationDisplay::getPatternColumns)
                            .flatMap(List::stream)
                            .collect(Collectors.toSet())
                    )
                    .orElseGet(Set::of);
            String reference = internationalizationDisplayEntry.getKey();
            Map<String, Configuration.ReferenceDescription> references = Optional.ofNullable(configuration.getReferences())
                    .orElse(new LinkedHashMap<>());
            if (!references.containsKey(reference)) {
                builder.unknownReferenceInDatatypeReferenceDisplay(dataType, reference, references.keySet());
                return;
            }


            Set<String> internationalizedColumns = getInternationalizedColumns(configuration, reference);
            Configuration.ReferenceDescription referenceDescription = configuration.getReferences().getOrDefault(reference, null);
            LinkedHashSet columns = Optional.ofNullable(referenceDescription)
                    .map(Configuration.ReferenceDescription::doGetStaticColumnDescriptions)
                    .map(c -> new LinkedHashSet(c.keySet()))
                    .orElseGet(LinkedHashSet::new);
            columns.addAll(internationalizedColumns);


            ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsForDisplay, columns).immutableCopy();
            if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
                builder.invalidInternationalizedColumnsForDataType(dataType, reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
            }
        }
    }

    private void verifyDatatypeRepository(String dataType, Configuration.DataTypeDescription dataTypeDescription, ConfigurationParsingResult.Builder builder) {
        final Configuration.RepositoryDescription repository = dataTypeDescription.getRepository();
        final String filePattern = Optional.ofNullable(repository).map(
                Configuration.RepositoryDescription::getFilePattern)
                .orElse(null);
        if (repository == null || Strings.isNullOrEmpty(filePattern)) {
            return;
        }
        final long countGroups = Arrays.stream(filePattern.split("\\("))
                .skip(1)
                .filter(s -> s.contains(")"))
                .count();
        if (countGroups == 0) {
            builder.noCapturingGroupForDatatypeRepository(dataType);
            return;
        }
        try {
            Pattern.compile(filePattern);
        } catch (Exception e) {
            builder.invalidPatternForDatatypeRepository(dataType);
            return;
        }
        extracted(dataType, builder, repository, countGroups, true);
        extracted(dataType, builder, repository, countGroups, false);
        try {
            final Map<String, Integer> authorizationScope = repository.getAuthorizationScope();
            if (authorizationScope != null && authorizationScope.size() > 0) {
                final LinkedHashMap<String, Configuration.AuthorizationScopeDescription> declaredAuthorizationScopes = Optional.ofNullable(dataTypeDescription.getAuthorization())
                        .map(Configuration.AuthorizationDescription::getAuthorizationScopes)
                        .orElseThrow(IllegalArgumentException::new);
                authorizationScope.entrySet().stream()
                        .forEach(scopeName -> {
                            if (declaredAuthorizationScopes.get(scopeName.getKey()) == null) {
                                throw new IllegalArgumentExceptionMap(Map.of(
                                        "scopeName", scopeName.getKey(),
                                        "registerScopes", declaredAuthorizationScopes.keySet()
                                ));
                            }
                            final Integer scopeToken = authorizationScope.get(scopeName.getKey());
                            if (scopeToken <= 0 || scopeToken > countGroups) {
                                builder.invalidCapturingGroupForDatatypeRepositoryAuthorizationScope(dataType, scopeToken, countGroups, scopeName.getKey());
                            }
                        });
            }
        } catch (IllegalArgumentExceptionMap e) {
            builder.invalidCapturingGroupForDatatypeRepository(dataType, e.messages);
        }

    }

    private void extracted(String dataType, ConfigurationParsingResult.Builder builder,  Configuration.RepositoryDescription repository, long countGroups, boolean isStart) {
        Optional.ofNullable(isStart ? repository.getStartDate() : repository.getEndDate())
                .map(Configuration.TokenDateDescription::getToken)
                .ifPresent(token -> {
                    if (token <= 0 || token > countGroups) {
                        builder.invalidCapturingGroupForDatatypeRepositoryDate(dataType, token, countGroups, isStart);
                    }
                });
    }


    private void verifyUniquenessComponentKeysInDatatype(String dataType, Configuration.DataTypeDescription dataTypeDescription, ConfigurationParsingResult.Builder builder) {
        final List<VariableComponentKey> uniqueness = dataTypeDescription.getUniqueness();
        final Set<String> availableVariableComponents = dataTypeDescription.getData().entrySet().stream()
                .flatMap(entry -> entry.getValue().doGetAllComponents().stream()
                        .map(componentName -> new VariableComponentKey(entry.getKey(), componentName).getId()))
                .collect(Collectors.<String>toSet());
        Set<String> variableComponentsKeyInUniqueness = new HashSet<>();
        for (VariableComponentKey variableComponentKey : uniqueness) {
            String id = variableComponentKey.getId();
            variableComponentsKeyInUniqueness.add(id);
        }
        ImmutableSet<String> unknownUsedAsVariableComponentUniqueness = Sets.difference(variableComponentsKeyInUniqueness, availableVariableComponents).immutableCopy();
        if (!unknownUsedAsVariableComponentUniqueness.isEmpty()) {
            builder.unknownUsedAsVariableComponentUniqueness(dataType, unknownUsedAsVariableComponentUniqueness, availableVariableComponents);
        }
    }

    private void verifyInternationalizedColumnsExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        Set<String> internationalizedColumns = getInternationalizedColumns(configuration, reference);
        Set<String> columns = Optional.ofNullable(referenceDescription)
                .map(Configuration.ReferenceDescription::doGetStaticColumnDescriptions)
                .map(Map::keySet)
                .orElse(new HashSet<>());

        ImmutableSet<String> internationalizedColumnsSet = ImmutableSet.copyOf(internationalizedColumns);
        ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsSet, columns).immutableCopy();
        if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
            builder.invalidInternationalizedColumns(reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
        }
    }

    private void verifyReferenceValidationRules(ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry, Set<String> references) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        LineValidationRuleDescriptionValidationContext lineValidationRuleDescriptionValidationContext = new LineValidationRuleDescriptionValidationContext() {

            @Override
            public Set<String> getReferenceCheckerRefTypeParameterValidValues() {
                return references;
            }

            @Override
            public Set<CheckerTarget> getAcceptableCheckerTargets() {
                return referenceDescription.doGetStaticColumns().stream()
                        .map(ReferenceColumn::new)
                        .collect(ImmutableSet.toImmutableSet());
            }

            @Override
            public void unknownCheckerNameForVariableComponentChecker(String validationRuleDescriptionEntryKey, CheckerType checkerName, ImmutableSet<CheckerType> checkerOnTargetNames) {
                builder.unknownCheckerNameForVariableComponentCheckerInReference(validationRuleDescriptionEntryKey, reference, checkerName, checkerOnTargetNames);
            }

            @Override
            public void unknownReferenceForChecker(String validationRuleDescriptionEntryKey, String refType, Set<String> references) {
                builder.unknownReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference, refType, references);
            }

            @Override
            public void missingReferenceForChecker(String validationRuleDescriptionEntryKey, Set<String> references) {
                builder.missingReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference, references);
            }

            @Override
            public void missingRequiredExpression(String validationRuleDescriptionEntryKey) {
                builder.missingRequiredExpressionForValidationRuleInReference(validationRuleDescriptionEntryKey, reference);
            }

            @Override
            public void illegalGroovyExpression(String validationRuleDescriptionEntryKey, String expression, GroovyExpression.CompilationError compilationError) {
                builder.illegalGroovyExpressionForValidationRuleInReference(validationRuleDescriptionEntryKey, reference, expression, compilationError);
            }

            @Override
            public void missingParamColumnReferenceForChecker(String validationRuleDescriptionEntryKey) {
                builder.missingParamColumnReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference);
            }

            @Override
            public void missingColumnReferenceForChecker(String validationRuleDescriptionEntryKey, CheckerType checkerName, Set<CheckerTarget> knownColumns, ImmutableSet<CheckerTarget> missingColumns) {
                builder.missingColumnReferenceForCheckerInReference(
                        validationRuleDescriptionEntryKey,
                        knownColumns.stream().map(CheckerTarget::toHumanReadableString).collect(ImmutableSet.toImmutableSet()),
                        checkerName,
                        missingColumns.stream().map(CheckerTarget::toHumanReadableString).collect(ImmutableSet.toImmutableSet()),
                        reference);
            }

            @Override
            public void unknownCheckerNameForValidationRule(String validationRuleDescriptionEntryKey, CheckerType checkerName, ImmutableSet<CheckerType> allCheckerNames) {
                builder.unknownCheckerNameForValidationRuleInReference(validationRuleDescriptionEntryKey, reference, checkerName, allCheckerNames);
            }

            @Override
            public void invalidPatternForDateChecker(String validationRuleDescriptionEntryKey, String pattern) {
                builder.invalidPatternForDateCheckerForValidationRuleInReference(validationRuleDescriptionEntryKey, reference, pattern);
            }

            @Override
            public void invalidDurationForDateChecker(String validationRuleDescriptionEntryKey, String duration) {
                builder.invalidDurationForDateCheckerForValidationRuleInReference(validationRuleDescriptionEntryKey, reference, duration);
            }

            @Override
            public void invalidPatternForRegularExpressionChecker(String validationRuleDescriptionEntryKey, String pattern) {
                builder.invalidPatternForRegularExpressionCheckerForValidationRuleInReference(validationRuleDescriptionEntryKey, reference, pattern);
            }

            @Override
            public void illegalCheckerConfigurationParameter(String validationRuleDescriptionEntryKey, CheckerType checkerName, String parameterName) {
                builder.illegalCheckerConfigurationParameterForValidationRuleInReference(validationRuleDescriptionEntryKey, reference, checkerName, parameterName);
            }
        };
        for (Map.Entry<String, Configuration.LineValidationRuleWithColumnsDescription> validationRuleDescriptionEntry : referenceDescription.getValidations().entrySet()) {
            String validationRuleDescriptionEntryKey = validationRuleDescriptionEntry.getKey();
            Configuration.LineValidationRuleWithColumnsDescription lineValidationRuleDescription = validationRuleDescriptionEntry.getValue();
            if (lineValidationRuleDescription.getColumns() == null) {
                System.out.println(lineValidationRuleDescription.getColumns());
                lineValidationRuleDescriptionValidationContext.missingParamColumnReferenceForChecker(validationRuleDescriptionEntryKey);
            } else
                verifyLineValidationRuleDescription(lineValidationRuleDescriptionValidationContext, validationRuleDescriptionEntryKey, lineValidationRuleDescription);
        }
    }

    /**
     * Vérifie une règle de validation exprimée dans le YAML.
     *
     * @param validationContext                 à fournir selon qu'on soit de valider une règle qui soit déclarée dans un référentiel ou un type de données
     * @param validationRuleDescriptionEntryKey le nom de la règle à valider
     * @param lineValidationRuleDescription     la configuration de la règle à valider
     */
    private void verifyLineValidationRuleDescription(LineValidationRuleDescriptionValidationContext validationContext, String validationRuleDescriptionEntryKey, Configuration.LineValidationRuleDescription lineValidationRuleDescription) {
        Configuration.CheckerDescription checker = lineValidationRuleDescription.getChecker();
        if (CheckerType.GroovyExpression.equals(checker.getName())) {
            String expression = Optional.of(checker)
                    .map(Configuration.CheckerDescription::getParams)
                    .map(Configuration.CheckerConfigurationDescription::getGroovy)
                    .map(GroovyConfiguration::getExpression)
                    .orElse(null);
            if (StringUtils.isBlank(expression)) {
                validationContext.missingRequiredExpression(validationRuleDescriptionEntryKey);
            } else {
                Optional<GroovyExpression.CompilationError> compileResult = GroovyLineChecker.validateExpression(expression);
                compileResult.ifPresent(compilationError -> validationContext.illegalGroovyExpression(validationRuleDescriptionEntryKey, expression, compilationError));
            }
        } else if (CHECKER_ON_TARGET_NAMES.contains(checker.getName())) {
            if (lineValidationRuleDescription.doGetCheckerTargets().isEmpty()) {
                validationContext.missingParamColumnReferenceForChecker(validationRuleDescriptionEntryKey);
            } else {
                Set<CheckerTarget> columnsDeclaredInCheckerConfiguration = lineValidationRuleDescription.doGetCheckerTargets();
                Set<CheckerTarget> knownColumns = validationContext.getAcceptableCheckerTargets();
                ImmutableSet<CheckerTarget> missingColumns = Sets.difference(columnsDeclaredInCheckerConfiguration, knownColumns).immutableCopy();
                if (!missingColumns.isEmpty()) {
                    validationContext.missingColumnReferenceForChecker(validationRuleDescriptionEntryKey, checker.getName(), knownColumns, missingColumns);
                }
            }
            CheckerOnOneTargetValidationContext checkerOnOneTargetValidationContext = new CheckerOnOneTargetValidationContext() {
                @Override
                public Set<String> getReferenceCheckerRefTypeParameterValidValues() {
                    return validationContext.getReferenceCheckerRefTypeParameterValidValues();
                }

                @Override
                public void unknownReferenceForChecker(String refType, Set<String> references) {
                    validationContext.unknownReferenceForChecker(validationRuleDescriptionEntryKey, refType, references);
                }

                @Override
                public void missingReferenceForChecker(Set<String> references) {
                    validationContext.missingReferenceForChecker(validationRuleDescriptionEntryKey, references);
                }

                @Override
                public void unknownCheckerOnOneTargetName(CheckerType checkerName, ImmutableSet<CheckerType> validCheckerNames) {
                    validationContext.unknownCheckerNameForVariableComponentChecker(validationRuleDescriptionEntryKey, checkerName, validCheckerNames);
                }

                @Override
                public void invalidPatternForDateChecker(String pattern) {
                    validationContext.invalidPatternForDateChecker(validationRuleDescriptionEntryKey, pattern);
                }

                @Override
                public void invalidDurationForDateChecker(String duration) {
                    validationContext.invalidDurationForDateChecker(validationRuleDescriptionEntryKey, duration);
                }

                @Override
                public void invalidPatternForRegularExpressionChecker(String pattern) {
                    validationContext.invalidPatternForRegularExpressionChecker(validationRuleDescriptionEntryKey, pattern);
                }

                @Override
                public void illegalCheckerConfigurationParameter(CheckerType checkerName, String parameterName) {
                    validationContext.illegalCheckerConfigurationParameter(validationRuleDescriptionEntryKey, checkerName, parameterName);
                }
            };
            verifyCheckerOnOneTarget(checkerOnOneTargetValidationContext, checker);
        } else {
            validationContext.unknownCheckerNameForValidationRule(validationRuleDescriptionEntryKey, checker.getName(), ALL_CHECKER_NAMES);
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

    private ConfigurationParsingResult onUnrecognizedPropertyException(UnrecognizedPropertyException e) {
        int lineNumber = e.getLocation().getLineNr();
        int columnNumber = e.getLocation().getColumnNr();
        String unknownPropertyName = e.getPropertyName();
        Collection<String> knownProperties = (Collection) e.getKnownPropertyIds();
        return ConfigurationParsingResult.builder()
                .unrecognizedProperty(lineNumber, columnNumber, unknownPropertyName, knownProperties)
                .build();
    }

    private ConfigurationParsingResult onInvalidFormatException(InvalidFormatException e) {
        String path = e.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .collect(Collectors.joining("->"));
        final String authorizedValues = Optional.ofNullable(e.getOriginalMessage())
                .map(m -> m.split(":"))
                .filter(l -> l.length > 2)
                .map(l -> l[2])
                .orElse("");
        int lineNumber = e.getLocation().getLineNr();
        int columnNumber = e.getLocation().getColumnNr();
        String value = e.getValue().toString();
        return ConfigurationParsingResult.builder()
                .invalidFormat(lineNumber, columnNumber, path, authorizedValues, value)
                .build();
    }

    /**
     * Pour lancer une validation d'un `checker` déclaré directement sur une donnée (colonne ou variable/composant).
     */
    private interface CheckerOnOneTargetValidationContext {

        /**
         * Si un checker de type 'Reference' est déclaré, l'ensemble des référentiels qui peuvent être utilisé
         */
        Set<String> getReferenceCheckerRefTypeParameterValidValues();

        void unknownReferenceForChecker(String refType, Set<String> references);

        void missingReferenceForChecker(Set<String> references);

        void unknownCheckerOnOneTargetName(CheckerType checkerName, ImmutableSet<CheckerType> validCheckerNames);

        void invalidPatternForDateChecker(String pattern);

        void invalidDurationForDateChecker(String duration);

        void invalidPatternForRegularExpressionChecker(String pattern);

        void illegalCheckerConfigurationParameter(CheckerType checkerName, String parameterName);
    }

    /**
     * Contexte qu'il faut passer pour vérifier qu'une règle de validation dans le YAML est correcte.
     */
    private interface LineValidationRuleDescriptionValidationContext {

        /**
         * Si un checker de type 'Reference' est déclaré, l'ensemble des référentiels qui peuvent être utilisé
         */
        Set<String> getReferenceCheckerRefTypeParameterValidValues();

        /**
         * Si le YAML exprime une règle de validation, l'ensemble des données qu'on peut accepter dans la configuration pour passer ce checker
         */
        Set<CheckerTarget> getAcceptableCheckerTargets();

        void missingRequiredExpression(String validationRuleDescriptionEntryKey);

        void illegalGroovyExpression(String validationRuleDescriptionEntryKey, String expression, GroovyExpression.CompilationError compilationError);

        void missingParamColumnReferenceForChecker(String validationRuleDescriptionEntryKey);

        void missingColumnReferenceForChecker(String validationRuleDescriptionEntryKey, CheckerType checkerName, Set<CheckerTarget> knownColumns, ImmutableSet<CheckerTarget> missingColumns);

        void unknownCheckerNameForVariableComponentChecker(String validationRuleDescriptionEntryKey, CheckerType checkerName, ImmutableSet<CheckerType> checkerOnTargetNames);

        void unknownReferenceForChecker(String validationRuleDescriptionEntryKey, String refType, Set<String> references);

        void missingReferenceForChecker(String validationRuleDescriptionEntryKey, Set<String> references);

        void unknownCheckerNameForValidationRule(String validationRuleDescriptionEntryKey, CheckerType checkerName, ImmutableSet<CheckerType> allCheckerNames);

        void invalidPatternForDateChecker(String validationRuleDescriptionEntryKey, String pattern);

        void invalidDurationForDateChecker(String validationRuleDescriptionEntryKey, String duration);

        void invalidPatternForRegularExpressionChecker(String validationRuleDescriptionEntryKey, String pattern);

        void illegalCheckerConfigurationParameter(String validationRuleDescriptionEntryKey, CheckerType checkerName, String parameterName);
    }

    @Getter
    @Setter
    @ToString
    private static class Versioned {
        int version;
    }

    private class IllegalArgumentExceptionMap extends IllegalArgumentException {
        Map<String, Object> messages;

        public IllegalArgumentExceptionMap(Map<String, Object> messages) {
            this.messages = messages;
        }
    }

}