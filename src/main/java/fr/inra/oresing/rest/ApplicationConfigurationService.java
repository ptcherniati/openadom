package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.GroovyLineChecker;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.VariableComponentKey;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Strings;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ApplicationConfigurationService {

    ConfigurationParsingResult parseConfigurationBytes(byte[] bytes) {
        if (bytes.length == 0) {
            return ConfigurationParsingResult.builder()
                    .recordEmptyFile()
                    .build();
        }

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

        for (Map.Entry<String, Configuration.ReferenceDescription> referenceEntry : configuration.getReferences().entrySet()) {
            verifyReferenceKeyColumnsExists(configuration, builder, referenceEntry);
            verifyValidationCheckersAreValids(configuration, builder, referenceEntry, references);
        }

        for (Map.Entry<String, Configuration.DataTypeDescription> entry : configuration.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            verifyDatatypeCheckersExists(builder, dataTypeDescription, dataType);
            verifyDatatypeCheckerReferenceRefersToExistingReference(builder, references, dataType, dataTypeDescription);
            verifyDatatypeCheckerGroovyExpressionExistsAndCanCompile(builder, dataTypeDescription);

            Set<String> variables = dataTypeDescription.getData().keySet();
            VariableComponentKey timeScopeVariableComponentKey = dataTypeDescription.getAuthorization().getTimeScope();
            verifyDatatypeTimeScopeExistsAndIsValid(builder, dataType, dataTypeDescription, variables, timeScopeVariableComponentKey);

            Multiset<String> variableOccurrencesInDataGroups = TreeMultiset.create();
            verifyDatatypeDataGroupsContainsExistingVariables(builder, dataTypeDescription, variables, variableOccurrencesInDataGroups);

            verifyDatatypeBindingToExistingVariableComponent(builder, variables, variableOccurrencesInDataGroups);

            verifyDatatypeBindingToExistingVariableComponent(builder, dataTypeDescription, variables);
        }

        return builder.build(configuration);
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
                            String pattern = timeScopeVariableComponentChecker.getParams().get(DateLineChecker.PARAM_PATTERN);
                            if (!LocalDateTimeRange.getKnownPatterns().contains(pattern)) {
                                builder.recordTimeScopeVariableComponentPatternUnknown(timeScopeVariableComponentKey, pattern, LocalDateTimeRange.getKnownPatterns());
                            }
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
                String expression = checker.getParams().get(GroovyLineChecker.PARAM_EXPRESSION);
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
                        if (checkerDescription.getParams() != null && checkerDescription.getParams().containsKey(ReferenceLineChecker.PARAM_REFTYPE)) {
                            String refType = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
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

    private void verifyReferenceKeyColumnsExists(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        if (referenceDescription == null) {
            builder.unknownReference(reference);
        } else {
            List<String> keyColumns = referenceDescription.getKeyColumns();
            Set<String> columns = referenceDescription.getColumns().keySet();
            ImmutableSet<String> keyColumnsSet = ImmutableSet.copyOf(keyColumns);
            ImmutableSet<String> unknownUsedAsKeyElementColumns = Sets.difference(keyColumnsSet, columns).immutableCopy();
            if (!unknownUsedAsKeyElementColumns.isEmpty()) {
                builder.recordInvalidKeyColumns(reference, unknownUsedAsKeyElementColumns, columns);
            }
        }
    }

    private void verifyValidationCheckersAreValids(Configuration configuration, ConfigurationParsingResult.Builder builder, Map.Entry<String, Configuration.ReferenceDescription> referenceEntry, Set<String> references) {
        String reference = referenceEntry.getKey();
        Configuration.ReferenceDescription referenceDescription = referenceEntry.getValue();
        if(referenceDescription != null) {
            for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationRuleDescriptionEntry : referenceDescription.getValidations().entrySet()) {
                String validationRuleDescriptionEntryKey = validationRuleDescriptionEntry.getKey();
                Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationRuleDescriptionEntry.getValue();
                Configuration.CheckerDescription checker = lineValidationRuleDescription.getChecker();
                if (checker == null) {
                    continue;
                }
                ImmutableSet<String> variableComponentCheckers = ImmutableSet.of("Date", "Float", "Integer", "RegularExpression", "Reference");
                String columns = checker.getParams().get(CheckerFactory.COLUMNS);

                if (GroovyLineChecker.NAME.equals(checker.getName())) {
                    String expression = checker.getParams().get(GroovyLineChecker.PARAM_EXPRESSION);
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
                        List<String> columnsList  = Stream.of(columns.split(",")).collect(Collectors.toList());
                        Set<String> availablesColumns = referenceDescription.getColumns().keySet();
                        List<String> missingColumns = columnsList.stream()
                                .filter(c -> !availablesColumns.contains(c))
                                .collect(Collectors.toList());
                        if (!missingColumns.isEmpty()) {
                            builder.missingColumnReferenceForCheckerInReference(validationRuleDescriptionEntryKey, availablesColumns, checker.getName(), missingColumns, reference);
                        }
                    }
                    if ("Reference".equals(checker.getName())) {
                        if (checker.getParams() != null && checker.getParams().containsKey(ReferenceLineChecker.PARAM_REFTYPE)) {
                            String refType = checker.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
                            if (!references.contains(refType)) {
                                //builder.unknownReferenceForCheckerInReference(validationRuleDescriptionEntryKey, reference, refType, references);
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
