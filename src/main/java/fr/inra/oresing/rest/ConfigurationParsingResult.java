package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.Collection;
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

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {

        private final List<ValidationCheckResult> validationCheckResults = new LinkedList<>();
        
        public Builder recordError(String message) {
            return recordError(message, ImmutableMap.of());
        }

        private Builder recordError(String message, Map<String, Object> params) {
            validationCheckResults.add(new ValidationCheckResult(false, message, params));
            return this;
        }

        public ConfigurationParsingResult build(Configuration configuration) {
            return new ConfigurationParsingResult(validationCheckResults, configuration);
        }

        public ConfigurationParsingResult build() {
            return new ConfigurationParsingResult(validationCheckResults, null);
        }

        public Builder recordEmptyFile() {
            return recordError("emptyFile");
        }

        public Builder recordUnableToParseYaml(String message) {
            return recordError(message);
        }

        public Builder recordUnsupportedVersion(int actualVersion, int expectedVersion) {
            return recordError("unsupportedVersion", ImmutableMap.of("actualVersion", actualVersion, "expectedVersion", expectedVersion));
        }

        public Builder missingReferenceForChecker(String dataType, String datum, String component, Set<String> references) {
            return recordError("missingReferenceForChecker", ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "component", component,
                    "references", references));
        }

        public Builder recordUndeclaredDataGroupForVariable(String variable) {
            return recordError("undeclaredDataGroupForVariable", ImmutableMap.of("variable", variable));
        }

        public Builder recordVariableInMultipleDataGroup(String variable) {
            return recordError("variableInMultipleDataGroup", ImmutableMap.of("variable", variable));
        }

        public Builder recordUnknownVariablesInDataGroup(String dataGroup, Set<String> unknownVariables, Set<String> variables) {
            return recordError("unknownVariablesInDataGroup", ImmutableMap.of(
                    "dataGroup", dataGroup,
                    "unknownVariables", unknownVariables,
                    "variables", variables)
            );
        }

        public Builder recordMissingTimeScopeVariableComponentKey(String dataType) {
            return recordError("missingTimeScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public Builder recordTimeScopeVariableComponentKeyMissingVariable(String dataType, Set<String> variables) {
            return recordError("timeScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "variables", variables));
        }

        public Builder recordTimeScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            return recordError("timeScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public Builder recordTimeVariableComponentKeyMissingComponent(String dataType, String variable, Set<String> knownComponents) {
            return recordError("timeVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public Builder recordTimeVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            return recordError("timeVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public Builder recordTimeScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            return recordError("timeScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public Builder recordTimeScopeVariableComponentPatternUnknown(VariableComponentKey timeScopeVariableComponentKey, String pattern, Set<String> knownPatterns) {
            return recordError("timeScopeVariableComponentPatternUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "pattern", pattern, "knownPatterns", knownPatterns));
        }

        public Builder recordUnrecognizedProperty(int lineNumber, int columnNumber, String unknownPropertyName, Collection<String> knownProperties) {
            return recordError("unrecognizedProperty", ImmutableMap.of(
                    "lineNumber", lineNumber,
                    "columnNumber", columnNumber,
                    "unknownPropertyName", unknownPropertyName,
                    "knownProperties", knownProperties
            ));
        }

        public Builder recordInvalidFormat(int lineNumber, int columnNumber, String value, String targetTypeName) {
            return recordError("invalidFormat", ImmutableMap.of(
                    "lineNumber", lineNumber,
                    "columnNumber", columnNumber,
                    "value", value,
                    "targetTypeName", targetTypeName
            ));
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
    // "unrecognizedProperty": "Erreur à la ligne {lineNumber} (colonne {columnNumber}) : {unknownPropertyName}, c'est pas une propriété reconnue. Les propriétés reconnues sont {knownProperties}"
    // "invalidFormat": "Erreur à la ligne {lineNumber} (colonne {columnNumber}) : '{value}' n’a pas le bon format. Le type attendu est {targetTypeName}"

}
