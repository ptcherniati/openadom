package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.*;

@Value
public class ConfigurationParsingResult {

    List<ValidationCheckResult> validationCheckResults;

    @Nullable
    Configuration result;

    public boolean isValid() {
        return getValidationCheckResults().stream().allMatch(ValidationCheckResult::isSuccess);
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

        private Builder recordError(String message, ImmutableMap<String, Object> params) {
            validationCheckResults.add(DefaultValidationCheckResult.error(message, params));
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

        public Builder unknownIllegalException(String cause)  {
            return recordError("unknownIllegalException", ImmutableMap.of(
                    "cause", cause));
        }

        public Builder missingReferenceForCheckerInReference(String validationKey, String reference, Set<String> references)  {
            return recordError("missingReferenceForCheckerInReference", ImmutableMap.of(
                    "validationKey", validationKey,
                    "reference", reference,
                    "references", references));
        }

        public Builder missingReferenceForChecker(String dataType, String datum, String component, Set<String> references) {
            return recordError("missingReferenceForChecker", ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "component", component,
                    "references", references));
        }

        public Builder unknownReferenceForCheckerInReference(String validationKey, String reference, String refType, Set<String> references) {
            return recordError("unknownReferenceForCheckerInReference", ImmutableMap.of(
                    "validationKey", validationKey,
                    "refType", refType,
                    "reference", reference,
                    "references", references));
        }

        public Builder unknownReferenceForChecker(String dataType, String datum, String component, String refType, Set<String> references) {
            return recordError("unknownReferenceForChecker", ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "refType", refType,
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

        public Builder recordMissingAuthorizationScopeVariableComponentKey(String dataType) {
            return recordError("missingAuthorizationScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public Builder recordTimeScopeVariableComponentKeyMissingVariable(String dataType, Set<String> variables) {
            return recordError("timeScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "variables", variables));
        }

        public Builder recordAuthorizationScopeVariableComponentKeyMissingVariable(String dataType, String authorizationScopeName, Set<String> variables) {
            return recordError("authorizationScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "authorizationScopeName", authorizationScopeName, "variables", variables));
        }

        public Builder recordTimeScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            return recordError("timeScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public Builder recordAuthorizationScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            return recordError("authorizationScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public Builder recordTimeVariableComponentKeyMissingComponent(String dataType, String variable, Set<String> knownComponents) {
            return recordError("timeVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public Builder recordAuthorizationVariableComponentKeyMissingComponent(String dataType, String authorizationName, String variable, Set<String> knownComponents) {
            return recordError("authorizationVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "authorizationName", authorizationName,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public Builder recordTimeVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            return recordError("timeVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public Builder recordAuthorizationVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            return recordError("authorizationVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public Builder recordTimeScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            return recordError("timeScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public Builder recordAuthorizationScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            return recordError("authorizationScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public Builder recordTimeScopeVariableComponentPatternUnknown(VariableComponentKey timeScopeVariableComponentKey, String pattern, Set<String> knownPatterns) {
            return recordError("timeScopeVariableComponentPatternUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "pattern", pattern, "knownPatterns", knownPatterns));
        }

        public Builder recordAuthorizationScopeVariableComponentReftypeUnknown(VariableComponentKey timeScopeVariableComponentKey, String refType, Set<String> knownPatterns) {
            return recordError("authorizationScopeVariableComponentReftypeUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "refType", refType, "knownPatterns", knownPatterns));
        }

        public Builder recordAuthorizationScopeVariableComponentReftypeNull(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownPatterns) {
            return recordError("authorizationScopeVariableComponentReftypeNull", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownPatterns", knownPatterns));
        }

        public Builder recordAuthorizationVariableComponentMustReferToCompositereference(String dataType, String authorizationName, String refType, Set<String> knownCompositesReferences) {
            return recordError("authorizationScopeVariableComponentReftypeUnknown", ImmutableMap.of("dataType", dataType, "authorizationName", authorizationName, "refType", refType, "knownCompositesReferences", knownCompositesReferences));
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

        public Builder recordMissingRequiredExpression(String lineValidationRuleKey) {
            return recordError("missingRequiredExpression", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey
            ));
        }

        public Builder recordIllegalGroovyExpression(String lineValidationRuleKey, String expression, GroovyExpression.CompilationError compilationError) {
            return recordError("illegalGroovyExpression", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "expression", expression,
                    "compilationError", compilationError
            ));
        }

        public Builder recordUnknownCheckerName(String lineValidationRuleKey, String checkerName) {
            return recordError("unknownCheckerName", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "checkerName", checkerName
            ));
        }

        public Builder recordUnknownCheckerNameForVariableComponentChecker(String dataType, String variable, String component, String checkerName, ImmutableSet<String> variableComponentCheckers) {
            return recordError("unknownCheckerNameForVariableComponent", ImmutableMap.of(
                    "datatype", dataType,
                    "variable", variable,
                    "component", component,
                    "checkerName", checkerName,
                    "knownsCheckers", variableComponentCheckers
            ));
        }

        public Builder recordCsvBoundToUnknownVariable(String header, String variable, Set<String> variables) {
            return recordError("csvBoundToUnknownVariable", ImmutableMap.of(
                    "header", header,
                    "variable", variable,
                    "variables", variables
            ));
        }

        public Builder recordCsvBoundToUnknownVariableComponent(String header, String variable, String component, Set<String> components) {
            return recordError("csvBoundToUnknownVariableComponent", ImmutableMap.of(
                    "header", header,
                    "variable", variable,
                    "component", component,
                    "components", components
            ));
        }

        public Builder recordInvalidKeyColumns(String reference, Set<String> unknownUsedAsKeyElementColumns, Set<String> knownColumns) {
            return recordError("invalidKeyColumns", ImmutableMap.of(
                    "reference", reference,
                    "unknownUsedAsKeyElementColumns", unknownUsedAsKeyElementColumns,
                    "knownColumns", knownColumns
            ));
        }

        public Builder recordInvalidInternationalizedColumns(String reference, Set<String> unknownUsedAsKeyInternationalizedColumns, Set<String> knownColumns) {
            return recordError("invalidInternationalizedColumns", ImmutableMap.of(
                    "reference", reference,
                    "unknownUsedAsInternationalizedColumns", unknownUsedAsKeyInternationalizedColumns,
                    "knownColumns", knownColumns
            ));
        }

        public Builder recordInvalidInternationalizedColumnsForDataType(String dataType, String reference, Set<String> unknownUsedAsKeyInternationalizedColumns, Set<String> knownColumns) {
            return recordError("invalidInternationalizedColumnsForDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "reference", reference,
                    "unknownUsedAsInternationalizedColumns", unknownUsedAsKeyInternationalizedColumns,
                    "knownColumns", knownColumns
            ));
        }

        public Builder missingColumnReferenceForCheckerInReference(String validationRuleDescriptionEntryKey, Set<String> availablesColumns, String name, List<String> missingColumns, String reference) {
            return recordError("missingColumnReferenceForCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "knownColumns", availablesColumns,
                    "checkerName", name,
                    "missingColumns", missingColumns
            ));
        }

        public Builder recordUnknownCheckerNameForVariableComponentCheckerInReference(String validationRuleDescriptionEntryKey, String reference, String name, ImmutableSet<String> variableComponentCheckers) {
            return recordError("unknownCheckerNameForVariableComponentCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "name", name,
                    "variableComponentCheckers", variableComponentCheckers
            ));
        }

        public Builder missingParamColumnReferenceForCheckerInReference(String validationRuleDescriptionEntryKey, String reference) {
            return recordError("missingParamColumnReferenceForCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey
            ));
        }

        public Builder missingAuthorizationsForDatatype(String dataType) {
            return recordError("missingAuthorizationForDatatype", ImmutableMap.of(
                    "datatype", dataType
            ));
        }

        public Builder recordUnknownReferenceInCompositeReference(String compositeReferenceName, ImmutableSet<String> unknownReferences, Set<String> existingReferences) {
            return recordError("unknownReferenceInCompositereference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "unknownReferences", unknownReferences,
                    "references", existingReferences)
            );
        }

        public Builder recordMissingReferenceInCompositereference(String compositeReferenceName) {
            return recordError("missingReferenceInCompositereference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName)
            );
        }

        public Builder recordRequiredReferenceInCompositeReferenceForParentKeyColumn(String compositeReferenceName, String parentKeyColumn) {
            return recordError("requiredReferenceInCompositeReferenceForParentKeyColumn", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "parentKeyColumn", parentKeyColumn)
            );
        }

        public Builder recordRequiredParentKeyColumnInCompositeReferenceForReference(String compositeReferenceName, String reference, String referenceTo) {
            return recordError("requiredParentKeyColumnInCompositeReferenceForReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "referenceTo", referenceTo)
            );
        }

        public Builder recordMissingParentColumnForReferenceInCompositeReferenceFor(String compositeReferenceName, String reference, String parentKeyColumn) {
            return recordError("missingParentColumnForReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "parentKeyColumn", parentKeyColumn)
            );
        }

        public Builder recordMissingParentRecursiveKeyColumnForReferenceInCompositeReference(String compositeReferenceName, String reference, String parentRecursiveKey) {
            return recordError("missingParentRecursiveKeyColumnForReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "parentRecursiveKey", parentRecursiveKey)
            );
        }

        public Builder recordUnknownReferenceInDatatypeReferenceDisplay(String dataType, String reference, Set<String> references) {
            return recordError("unknownReferenceInDatatypeReferenceDisplay", ImmutableMap.of(
                    "dataType", dataType,
                    "reference", reference,
                    "references", references)
            );
        }

        public Builder recordMissingKeyColumnsForReference(String reference) {
            return recordError("missingKeyColumnsForReference", ImmutableMap.of(
                    "reference", reference)
            );
        }
    }
}