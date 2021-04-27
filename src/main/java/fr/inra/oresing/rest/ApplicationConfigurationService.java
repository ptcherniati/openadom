package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import fr.inra.oresing.checker.DateChecker;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.VariableComponentKey;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
public class ApplicationConfigurationService {

    ConfigurationParsingResult parseConfigurationBytes(byte[] bytes) {
        ConfigurationParsingResult.Builder builder = ConfigurationParsingResult.builder();
        if (bytes.length == 0) {
            builder.recordEmptyFile();
            return builder.build();
        } else {
            try {
                YAMLMapper mapper = new YAMLMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                Versioned versioned = mapper.readValue(bytes, Versioned.class);
                int actualVersion = versioned.getVersion();
                int expectedVersion = 0;
                if (actualVersion != expectedVersion) {
                    builder.recordUnsupportedVersion(actualVersion, expectedVersion);
                    return builder.build();
                }
            } catch (Exception e) {
                builder.recordUnableToParseVersion(e.getMessage());
                return builder.build();
            }

            Configuration configuration;
            try {
                YAMLMapper mapper = new YAMLMapper();
                configuration = mapper.readValue(bytes, Configuration.class);
            } catch (Exception e) {
                builder.recordUnableToParseYaml(e.getMessage());
                return builder.build();
            }

            Set<String> references = configuration.getReferences() == null ? Collections.emptySet() : configuration.getReferences().keySet();
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
                                if (checkerDescription.getParams().containsKey(ReferenceChecker.PARAM_REFTYPE)) {
                                    // OK
                                } else {
                                    builder.missingReferenceForChecker(dataType, datum, component, references);
                                }
                            }
                        }
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
                String pattern = timeScopeVariableComponentChecker.getParams().get(DateChecker.PARAM_PATTERN);
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
    }

    @Getter
    @Setter
    @ToString
    private static class Versioned {
        int version;
    }

}
