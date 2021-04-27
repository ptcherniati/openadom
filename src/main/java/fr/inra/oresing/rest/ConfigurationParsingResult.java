package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
public class ConfigurationParsingResult {

    List<ValidationCheckResult> validationCheckResults;

    @Nullable
    Configuration result;

    public boolean isValid() {
        return getValidationCheckResults().stream().allMatch(ValidationCheckResult::isValid);
    }

    @Nullable
    public Configuration getResult() {
        return result;
    }

    @Value
    static class ValidationCheckResult {
        boolean valid;
        String message;
        Map<String, Object> messageParams;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {

        private final List<ValidationCheckResult> validationCheckResults = new LinkedList<>();
        
        public void recordError(String message) {
            recordError(message, ImmutableMap.of());
        }

        private void recordError(String message, Map<String, Object> params) {
            validationCheckResults.add(new ValidationCheckResult(false, message, params));
        }

        public ConfigurationParsingResult build(Configuration configuration) {
            return new ConfigurationParsingResult(validationCheckResults, configuration);
        }

        public ConfigurationParsingResult build() {
            return new ConfigurationParsingResult(validationCheckResults, null);
        }

        public void recordEmptyFile() {
            recordError("emptyFile");
        }

        public void recordUnableToParseVersion(String message) {
            recordError(message);
        }

        public void recordUnableToParseYaml(String message) {
            recordError(message);
        }

        public void recordUnsupportedVersion(int actualVersion, int expectedVersion) {
            ImmutableMap<String, Object> params = ImmutableMap.of("actualVersion", actualVersion, "expectedVersion", expectedVersion);
            recordError("unsupportedVersion", params);
        }

        public void missingReferenceForChecker(String dataType, String datum, String component, Set<String> references) {
            ImmutableMap<String, Object> params = ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "component", component,
                    "references", references);
            recordError("missingReferenceForChecker", params);
        }

        public void recordUndeclaredDataGroupForVariable(String variable) {
            recordError("undeclaredDataGroupForVariable", ImmutableMap.of("variable", variable));
        }

        public void recordVariableInMultipleDataGroup(String variable) {
            recordError("variableInMultipleDataGroup", ImmutableMap.of("variable", variable));
        }

        public void recordUnknownVariablesInDataGroup(String dataGroup, Set<String> unknownVariables, Set<String> variables) {
            recordError("unknownVariablesInDataGroup", ImmutableMap.of(
                    "dataGroup", dataGroup,
                    "unknownVariables", unknownVariables,
                    "variables", variables)
            );
        }

        public void recordMissingTimeScopeVariableComponentKey(String dataType) {
            recordError("missingTimeScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public void recordTimeScopeVariableComponentKeyMissingVariable(String dataType, Set<String> variables) {
            recordError("timeScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "variables", variables));
        }

        public void recordTimeScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            recordError("timeScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public void recordTimeVariableComponentKeyMissingComponent(String dataType, String variable, Set<String> knownComponents) {
            recordError("timeVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public void recordTimeVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            recordError("timeVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public void recordTimeScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            recordError("timeScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public void recordTimeScopeVariableComponentPatternUnknown(VariableComponentKey timeScopeVariableComponentKey, String pattern, Set<String> knownPatterns) {
            recordError("timeScopeVariableComponentPatternUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "pattern", pattern, "knownPatterns", knownPatterns));
        }
    }

    // "emptyFile": "le fichier est vide"
    // "missingReferenceForChecker": "Pour le type de données {dataType}, la donnée {datum}, le composant {component}, il faut préciser le référentiel parmi {references}"
    // "unsupportedVersion": "Les fichiers YAML de version {actualVersion} ne sont pas géré, version attendue {expectedVersion}"
    // "undeclaredDataGroupForVariable": "la variable {variable} n’est déclarée appartenir à aucun groupe de données, elle doit être présente dans un groupe"
    // "variableInMultipleDataGroup": "la variable {variable} est déclarée dans plusieurs groupes de données, elle ne doit être présente que dans un groupe"
    // "unknownVariablesInDataGroup": "le groupe de données {dataGroup} contient des données qui ne sont pas déclarées {unknownVariables}. Données connues {variables}"
    // "missingTimeScopeVariableComponentKey": "il faut indiquer la variable (et son composant) dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données {dataType}"
    // "timeScopeVariableComponentKeyMissingVariable": "il faut indiquer la variable dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données {dataType}. Valeurs possibles {variables}"
    // "timeScopeVariableComponentKeyUnknownVariable": "{variable} ne fait pas parti des colonnes connues {knownVariables}"
    // "timeVariableComponentKeyMissingComponent": "il faut indiquer le composant de la variable {variable} dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données {dataType}. Valeurs possibles {knownComponents}"
    // "timeVariableComponentKeyUnknownComponent": "{component} ne fait pas parti des composants connus pour la variable {variable}. Composants connus : {knownComponents}"
    // "timeScopeVariableComponentWrongChecker": "Le composant {component} de la variable {variable} ne peut pas être utilisé comme portant l’information temporelle car ce n’est pas une donnée déclarée comme {expectedChecker}"
    // "timeScopeVariableComponentPatternUnknown": "Le composant {component} de la variable {variable} ne peut pas être utilisé comme portant l’information temporelle car le format de date '{pattern}' n’est pas géré. Formats acceptés : {knownPatterns}"

}
