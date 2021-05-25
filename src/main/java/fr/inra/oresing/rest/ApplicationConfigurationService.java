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
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.GroovyLineChecker;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.VariableComponentKey;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        for (Map.Entry<String, Configuration.DataTypeDescription> entry : configuration.getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            for (Map.Entry<String, Configuration.ColumnDescription> dataEntry : dataTypeDescription.getData().entrySet()) {
                String datum = dataEntry.getKey();
                Configuration.ColumnDescription datumDescription = dataEntry.getValue();
                for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : datumDescription.getComponents().entrySet()) {
                    String component = componentEntry.getKey();
                    Configuration.VariableComponentDescription variableComponentDescription = componentEntry.getValue();
                    if (variableComponentDescription != null) {
                        Configuration.CheckerDescription checkerDescription = variableComponentDescription.getChecker();
                        if ("Reference".equals(checkerDescription.getName())) {
                            if (checkerDescription.getParams().containsKey(ReferenceLineChecker.PARAM_REFTYPE)) {
                                // OK
                            } else {
                                builder.missingReferenceForChecker(dataType, datum, component, references);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : dataTypeDescription.getValidations().entrySet()) {
                Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
                String lineValidationRuleKey = validationEntry.getKey();
                Configuration.CheckerDescription checker = lineValidationRuleDescription.getChecker();
                if (GroovyLineChecker.NAME.equals(checker.getName())) {
                    String expression = checker.getParams().get(GroovyLineChecker.PARAM_EXPRESSION);
                    if (StringUtils.isBlank(expression)) {
                        builder.recordMissingRequiredExpression(lineValidationRuleKey);
                    } else {
                        Optional<GroovyLineChecker.CompilationError> compileResult = GroovyLineChecker.validateExpression(expression);
                        compileResult.ifPresent(compilationError -> builder.recordIllegalGroovyExpression(lineValidationRuleKey, expression, compilationError));
                    }
                } else {
                    builder.recordUnknownCheckerName(lineValidationRuleKey, checker.getName());
                }
            }

            Set<String> variables = dataTypeDescription.getData().keySet();

            VariableComponentKey timeScopeVariableComponentKey = dataTypeDescription.getAuthorization().getTimeScope();
            if (timeScopeVariableComponentKey == null) {
                builder.recordMissingTimeScopeVariableComponentKey(dataType);
            }
            if (timeScopeVariableComponentKey.getVariable() == null) {
                builder.recordTimeScopeVariableComponentKeyMissingVariable(dataType, variables);
            }
            if (!dataTypeDescription.getData().containsKey(timeScopeVariableComponentKey.getVariable())) {
                builder.recordTimeScopeVariableComponentKeyUnknownVariable(timeScopeVariableComponentKey, variables);
            }
            if (timeScopeVariableComponentKey.getComponent() == null) {
                builder.recordTimeVariableComponentKeyMissingComponent(dataType, timeScopeVariableComponentKey.getVariable(), dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().keySet());
            }
            if (!dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().containsKey(timeScopeVariableComponentKey.getComponent())) {
                builder.recordTimeVariableComponentKeyUnknownComponent(timeScopeVariableComponentKey, dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().keySet());
            }
            Configuration.CheckerDescription timeScopeVariableComponentChecker = dataTypeDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().get(timeScopeVariableComponentKey.getComponent()).getChecker();
            if (timeScopeVariableComponentChecker == null || !"Date".equals(timeScopeVariableComponentChecker.getName())) {
                builder.recordTimeScopeVariableComponentWrongChecker(timeScopeVariableComponentKey, "Date");
            }
            String pattern = timeScopeVariableComponentChecker.getParams().get(DateLineChecker.PARAM_PATTERN);
            if (!LocalDateTimeRange.getKnownPatterns().contains(pattern)) {
                builder.recordTimeScopeVariableComponentPatternUnknown(timeScopeVariableComponentKey, pattern, LocalDateTimeRange.getKnownPatterns());
            }

            Multiset<String> variableOccurrencesInDataGroups = TreeMultiset.create();
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

            variables.forEach(variable -> {
                int count = variableOccurrencesInDataGroups.count(variable);
                if (count == 0) {
                    builder.recordUndeclaredDataGroupForVariable(variable);
                } else if (count > 1) {
                    builder.recordVariableInMultipleDataGroup(variable);
                }
            });
        }

        return builder.build(configuration);
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
