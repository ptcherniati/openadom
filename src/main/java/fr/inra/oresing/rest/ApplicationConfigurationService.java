package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import fr.inra.oresing.model.internationalization.InternationalizationDataTypeMap;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import fr.inra.oresing.model.internationalization.InternationalizationReferenceMap;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ApplicationConfigurationService {
    public static final List INTERNATIONALIZED_FIELDS = List.of("internationalization", "internationalizationName", "internationalizedColumns", "internationalizationDisplay");


    ConfigurationParsingResult unzipConfiguration(MultipartFile file){
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
            configuration = mapper.readValue(bytes, Configuration.class);

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
            verifyReferenceKeyColumns( builder, referenceEntry);
            verifyInternationalizedColumnsExists(configuration, builder, referenceEntry);
            verifyInternationalizedColumnsExistsForPattern(configuration, builder, referenceEntry);
            verifyValidationCheckersAreValids( builder, referenceEntry, references);
        }

        for (Map.Entry<String, Configuration.DataTypeDescription> entry : configuration.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            verifyDatatypeCheckersExists(builder, dataTypeDescription, dataType);
            verifyDatatypeCheckerReferenceRefersToExistingReference(builder, references, dataType, dataTypeDescription);
            verifyDatatypeCheckerGroovyExpressionExistsAndCanCompile(builder, dataTypeDescription);
            verifyInternationalizedColumnsExistsForPatternInDatatype(configuration, builder, dataType);
            verifyUniquenessComponentKeysInDatatype(dataType, dataTypeDescription, builder);

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

                LinkedHashMap<String, Configuration.AuthorizationScopeDescription> authorizationScopesVariableComponentKey = authorization.getAuthorizationScopes();
                verifyDatatypeAuthorizationScopeExistsAndIsValid(builder, dataType, configuration, variables, authorizationScopesVariableComponentKey);
                requiredAuthorizationsAttributesBuilder.addAll(authorizationScopesVariableComponentKey.keySet());
            }

            Multiset<String> variableOccurrencesInDataGroups = TreeMultiset.create();
            verifyDatatypeDataGroupsContainsExistingVariables(builder, dataTypeDescription, variables, variableOccurrencesInDataGroups);

            verifyDatatypeBindingToExistingVariableComponent(builder, variables, variableOccurrencesInDataGroups);
            verifyDatatypeBindingToExistingVariableComponent(builder, dataTypeDescription, dataType, variables);
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
                .map(Configuration.CompositeReferenceComponentDescription::getReference)
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
                    builder.recordCsvBoundToUnknownVariableComponent(columnBindingDescription.getHeader(), variable, component, components);
                }
            } else {
                builder.recordCsvBoundToUnknownVariable(columnBindingDescription.getHeader(), variable, variables);
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
                        builder.recordCsvSameHeaderLineAndFirstRowLineForConstantDescription(dataType);
                    }
                    final int firstRowLine = format.getFirstRowLine();
                    if (rowNumber >= firstRowLine) {
                        builder.recordCsvTooBigRowLineForConstantDescription(dataType);
                    }
                    if (rowNumber < 1) {
                        builder.recordCsvTooLittleRowLineForConstantDescription(dataType);
                    }
                    if (rowNumber < headerLine && rowNumber < 1) {
                        builder.recordCsvMissingRowLineForConstantDescription(dataType);
                    } else if (rowNumber > headerLine && columnNumber < 1 && headerName == null) {
                        builder.recordCsvMissingColumnNumberOrHeaderNameForConstantDescription(dataType);
                    } else {
                        final VariableComponentKey boundTo = headerConstantDescription.getBoundTo();
                        if (boundTo == null) {
                            builder.recordCsvMissingBoundToForConstantDescription(dataType);
                        } else if (headerConstantDescription.getExportHeader() == null) {
                            builder.recordCsvMissingExportHeaderNameForConstantDescription(dataType);
                        }
                    }
                });
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

    private void verifyDatatypeAuthorizationScopeExistsAndIsValid(ConfigurationParsingResult.Builder builder, String dataType, Configuration configuration, Set<String> variables, LinkedHashMap<String, Configuration.AuthorizationScopeDescription> authorizationScopesVariableComponentKey) {
        if (authorizationScopesVariableComponentKey == null || authorizationScopesVariableComponentKey.isEmpty()) {
            builder.recordMissingAuthorizationScopeVariableComponentKey(dataType);
        } else {
            Configuration.DataTypeDescription dataTypeDescription = configuration.getDataTypes().get(dataType);
            authorizationScopesVariableComponentKey.entrySet().stream().forEach(authorizationScopeVariableComponentKeyEntry -> {
                String authorizationScopeName = authorizationScopeVariableComponentKeyEntry.getKey();
                Configuration.AuthorizationScopeDescription authorizationScopeDescription = authorizationScopeVariableComponentKeyEntry.getValue();
                VariableComponentKey authorizationScopeVariableComponentKey = authorizationScopeDescription.getVariableComponentKey();
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
                                String refType;
                                Configuration.CheckerConfigurationDescription checkerConfigurationDescription = null;
                                if (authorizationScopeVariableComponentChecker != null) {
                                    checkerConfigurationDescription = authorizationScopeVariableComponentChecker.getParams();
                                }
                                if (checkerConfigurationDescription == null) {
                                    builder.recordAuthorizationScopeVariableComponentReftypeNull(authorizationScopeVariableComponentKey, configuration.getReferences().keySet());
                                } else {
                                    refType = checkerConfigurationDescription.getRefType();
                                    if (refType == null || !configuration.getReferences().containsKey(refType)) {
                                        builder.recordAuthorizationScopeVariableComponentReftypeUnknown(authorizationScopeVariableComponentKey, refType, configuration.getReferences().keySet());
                                    } else {
                                        Set<String> compositesReferences = configuration.getCompositeReferences().values().stream()
                                                .map(Configuration.CompositeReferenceDescription::getComponents)
                                                .flatMap(List::stream)
                                                .map(Configuration.CompositeReferenceComponentDescription::getReference)
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
                                    .map(Configuration.CheckerDescription::getParams)
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

    private void verifyReferenceKeyColumns(ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
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
                .map(Configuration.ReferenceDescription::getColumns)
                .map(c -> new LinkedHashSet(c.keySet()))
                .orElseGet(LinkedHashSet::new);
        columns.addAll(internationalizedColumns);


        ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsForDisplay, columns).immutableCopy();
        if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
            builder.recordInvalidInternationalizedColumns(reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
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
                builder.recordUnknownReferenceInDatatypeReferenceDisplay(dataType, reference, references.keySet());
                return;
            }


            Set<String> internationalizedColumns = getInternationalizedColumns(configuration, reference);
            Configuration.ReferenceDescription referenceDescription = configuration.getReferences().getOrDefault(reference, null);
            LinkedHashSet columns = Optional.ofNullable(referenceDescription)
                    .map(Configuration.ReferenceDescription::getColumns)
                    .map(c -> new LinkedHashSet(c.keySet()))
                    .orElseGet(LinkedHashSet::new);
            columns.addAll(internationalizedColumns);


            ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsForDisplay, columns).immutableCopy();
            if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
                builder.recordInvalidInternationalizedColumnsForDataType(dataType, reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
            }
        }
    }

    private void verifyUniquenessComponentKeysInDatatype(String dataType, Configuration.DataTypeDescription dataTypeDescription, ConfigurationParsingResult.Builder builder) {
        final List<VariableComponentKey> uniqueness = dataTypeDescription.getUniqueness();
        final Set<String> availableVariableComponents = dataTypeDescription.getData().entrySet().stream()
                .flatMap(entry -> entry.getValue().getComponents().keySet().stream()
                        .map(componentName -> new VariableComponentKey(entry.getKey(), componentName).getId()))
                .collect(Collectors.<String>toSet());
        Set<String> variableComponentsKeyInUniqueness = new HashSet<>();
        for (VariableComponentKey variableComponentKey : uniqueness) {
            String id = variableComponentKey.getId();
            variableComponentsKeyInUniqueness.add(id);
        }
        ImmutableSet<String> unknownUsedAsVariableComponentUniqueness = Sets.difference(variableComponentsKeyInUniqueness, availableVariableComponents).immutableCopy();
        if (!unknownUsedAsVariableComponentUniqueness.isEmpty()) {
            builder.recordUnknownUsedAsVariableComponentUniqueness(dataType, unknownUsedAsVariableComponentUniqueness, availableVariableComponents);
        }
    }

    private void verifyInternationalizedColumnsExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        Set<String> internationalizedColumns = getInternationalizedColumns(configuration, reference);
        Set<String> columns = Optional.ofNullable(referenceDescription)
                .map(Configuration.ReferenceDescription::getColumns)
                .map(LinkedHashMap::keySet)
                .orElse(new HashSet<>());

        ImmutableSet<String> internationalizedColumnsSet = ImmutableSet.copyOf(internationalizedColumns);
        ImmutableSet<String> unknownUsedAsInternationalizedColumnsSetColumns = Sets.difference(internationalizedColumnsSet, columns).immutableCopy();
        if (!unknownUsedAsInternationalizedColumnsSetColumns.isEmpty()) {
            builder.recordInvalidInternationalizedColumns(reference, unknownUsedAsInternationalizedColumnsSetColumns, columns);
        }
    }

    private void verifyValidationCheckersAreValids(ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry, Set<String> references) {
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
            // autant mettre une collection dans le YAML directement
            Set<String> groovyColumn = Set.of();
            Configuration.CheckerConfigurationDescription params = checker.getParams();
            if (params != null && params.getGroovy() != null) {
                String values = MoreObjects.firstNonNull(params.getColumns(), "");
                if (values != null) {
                    String[] split = values.split(",");
                    groovyColumn = Arrays.stream(split).collect(Collectors.toSet());
                }
            }

            if (GroovyLineChecker.NAME.equals(checker.getName())) {
                String expression = Optional.of(checker)
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
        exceptions
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