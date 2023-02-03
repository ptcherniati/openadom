package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.checker.CheckerType;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.*;

@Value
public class ConfigurationParsingResult {

    List<ValidationCheckResult> validationCheckResults;

    @Nullable
    Configuration result;

    public static Builder builder() {
        return new Builder();
    }

    public boolean isValid() {
        return getValidationCheckResults().stream().allMatch(ValidationCheckResult::isSuccess);
    }

    @Nullable
    public Configuration getResult() {
        return result;
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

        public Builder emptyFile() {
            return recordError("emptyFile");
        }

        public Builder recordUnableToParseYaml(String message) {
            return recordError(message);
        }

        public Builder unsupportedVersion(int actualVersion, int expectedVersion) {
            return recordError("unsupportedVersion", ImmutableMap.of("actualVersion", actualVersion, "expectedVersion", expectedVersion));
        }

        public void unknownIllegalException(String cause) {
            recordError("unknownIllegalException", ImmutableMap.of(
                    "cause", cause));
        }

        public void missingReferenceForCheckerInReference(String validationKey, String reference, Set<String> references) {
            recordError("missingReferenceForCheckerInReference", ImmutableMap.of(
                    "validationKey", validationKey,
                    "reference", reference,
                    "references", references));
        }

        public void missingReferenceForCheckerInDataType(String validationKey, String dataType, Set<String> references) {
            recordError("missingReferenceForCheckerInDataType", ImmutableMap.of(
                    "validationKey", validationKey,
                    "dataType", dataType,
                    "references", references));
        }

        public void missingReferenceForChecker(String dataType, String datum, String component, Set<String> references) {
            recordError("missingReferenceForChecker", ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "component", component,
                    "references", references));
        }

        public void unknownReferenceForCheckerInReference(String validationKey, String reference, String refType, Set<String> references) {
            recordError("unknownReferenceForCheckerInReference", ImmutableMap.of(
                    "validationKey", validationKey,
                    "refType", refType,
                    "reference", reference,
                    "references", references));
        }

        public void unknownReferenceForCheckerInDataType(String validationKey, String dataType, String refType, Set<String> references) {
            recordError("unknownReferenceForCheckerInDataType", ImmutableMap.of(
                    "validationKey", validationKey,
                    "refType", refType,
                    "dataType", dataType,
                    "references", references));
        }

        public void unknownReferenceForChecker(String dataType, String datum, String component, String refType, Set<String> references) {
            recordError("unknownReferenceForChecker", ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "refType", refType,
                    "component", component,
                    "references", references));
        }

        public void undeclaredDataGroupForVariable(String variable, String dataType) {
            recordError("undeclaredDataGroupForVariable", ImmutableMap.of(
                    "variable", variable,
                    "dataType", dataType)
            );
        }

        public void variableInMultipleDataGroup(String variable, String dataType) {
            recordError("variableInMultipleDataGroup", ImmutableMap.of(
                    "variable", variable,
                    "dataType", dataType)
            );
        }

        public void unknownVariablesInDataGroup(String dataGroup, Set<String> unknownVariables, Set<String> variables, String dataType) {
            recordError("unknownVariablesInDataGroup", ImmutableMap.of(
                    "dataGroup", dataGroup,
                    "unknownVariables", unknownVariables,
                    "variables", variables,
                    "dataType", dataType)
            );
        }

        public void missingTimeScopeVariableComponentKey(String dataType) {
            recordError("missingTimeScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public void missingAuthorizationScopeVariableComponentKey(String dataType) {
            recordError("missingAuthorizationScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public void timeScopeVariableComponentKeyMissingVariable(String dataType, Set<String> variables) {
            recordError("timeScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "variables", variables));
        }

        public void authorizationScopeVariableComponentKeyMissingVariable(String dataType, String authorizationScopeName, Set<String> variables) {
            recordError("authorizationScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "authorizationScopeName", authorizationScopeName, "variables", variables));
        }

        public void timeScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            recordError("timeScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public void authorizationScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            recordError("authorizationScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public void timeVariableComponentKeyMissingComponent(String dataType, String variable, Set<String> knownComponents) {
            recordError("timeVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public void authorizationVariableComponentKeyMissingComponent(String dataType, String authorizationName, String variable, Set<String> knownComponents) {
            recordError("authorizationVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "authorizationName", authorizationName,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public void timeVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            recordError("timeVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public void authorizationVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            recordError("authorizationVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public void timeScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            recordError("timeScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public void authorizationScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            recordError("authorizationScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public void timeScopeVariableComponentPatternUnknown(VariableComponentKey timeScopeVariableComponentKey, String pattern, Set<String> knownPatterns) {
            recordError("timeScopeVariableComponentPatternUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "pattern", pattern, "knownPatterns", knownPatterns));
        }

        public void authorizationScopeVariableComponentReftypeUnknown(VariableComponentKey timeScopeVariableComponentKey, String refType, Set<String> knownPatterns) {
            recordError("authorizationScopeVariableComponentReftypeUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "refType", refType, "knownPatterns", knownPatterns));
        }

        public void authorizationScopeVariableComponentReftypeNull(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownPatterns) {
            recordError("authorizationScopeVariableComponentReftypeNull", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownPatterns", knownPatterns));
        }

        public void authorizationScopeVariableComponentReftypeUnknown(String dataType, String authorizationName, String refType, Set<String> knownCompositesReferences) {
            recordError("authorizationScopeVariableComponentReftypeUnknown", ImmutableMap.of("dataType", dataType, "authorizationName", authorizationName, "refType", refType, "knownCompositesReferences", knownCompositesReferences));
        }

        public Builder unrecognizedProperty(int lineNumber, int columnNumber, String unknownPropertyName, Collection<String> knownProperties) {
            return recordError("unrecognizedProperty", ImmutableMap.of(
                    "lineNumber", lineNumber,
                    "columnNumber", columnNumber,
                    "unknownPropertyName", unknownPropertyName,
                    "knownProperties", knownProperties
            ));
        }

        public Builder invalidFormat(int lineNumber, int columnNumber, String path, String authorizedValues, String value) {
            final ImmutableMap<String, Object> map = ImmutableMap.copyOf(Map.of(
                    "lineNumber", lineNumber,
                    "columnNumber", columnNumber,
                    "path", path,
                    "authorizedValues", authorizedValues,
                    "value", value
            ));
            return recordError("invalidFormat", map);
        }

        public void missingRequiredExpressionForValidationRuleInDataType(String lineValidationRuleKey, String dataType) {
            recordError("missingRequiredExpressionForValidationRuleInDataType", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "dataType", dataType
            ));
        }

        public void missingRequiredExpressionForValidationRuleInReference(String lineValidationRuleKey, String reference) {
            recordError("missingRequiredExpressionForValidationRuleInReference", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "reference", reference
            ));
        }

        public void illegalGroovyExpressionForValidationRuleInDataType(String lineValidationRuleKey, String dataType, String expression, GroovyExpression.CompilationError compilationError) {
            recordError("illegalGroovyExpressionForValidationRuleInDataType", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "dataType", dataType,
                    "expression", expression,
                    "compilationError", compilationError
            ));
        }

        public void illegalGroovyExpressionForValidationRuleInReference(String lineValidationRuleKey, String reference, String expression, GroovyExpression.CompilationError compilationError) {
            recordError("illegalGroovyExpressionForValidationRuleInReference", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "reference", reference,
                    "expression", expression,
                    "compilationError", compilationError
            ));
        }

        public void unknownCheckerNameForValidationRuleInReference(String lineValidationRuleKey, String reference, CheckerType checkerName, ImmutableSet<CheckerType> allCheckerNames) {
            recordError("unknownCheckerNameForValidationRuleInReference", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "reference", reference,
                    "allCheckerNames", allCheckerNames,
                    "checkerName", checkerName
            ));
        }

        public void unknownCheckerNameForValidationRuleInDataType(String lineValidationRuleKey, String dataType, CheckerType checkerName, ImmutableSet<CheckerType> allCheckerNames) {
            recordError("unknownCheckerNameForValidationRuleInDataType", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "dataType", dataType,
                    "allCheckerNames", allCheckerNames,
                    "checkerName", checkerName
            ));
        }

        public void unknownCheckerNameForVariableComponent(String dataType, String variable, String component, CheckerType checkerName, ImmutableSet<CheckerType> knownCheckerNames) {
            recordError("unknownCheckerNameForVariableComponent", ImmutableMap.of(
                    "datatype", dataType,
                    "variable", variable,
                    "component", component,
                    "checkerName", checkerName,
                    "knownCheckerNames", knownCheckerNames
            ));
        }

        public void csvBoundToUnknownVariable(String header, String variable, Set<String> variables) {
            recordError("csvBoundToUnknownVariable", ImmutableMap.of(
                    "header", header,
                    "variable", variable,
                    "variables", variables
            ));
        }

        public void csvBoundToUnknownVariableComponent(String header, String variable, String component, Set<String> components) {
            recordError("csvBoundToUnknownVariableComponent", ImmutableMap.of(
                    "header", header,
                    "variable", variable,
                    "component", component,
                    "components", components
            ));
        }

        public void invalidKeyColumns(String reference, Set<String> unknownUsedAsKeyElementColumns, Set<String> knownColumns) {
            recordError("invalidKeyColumns", ImmutableMap.of(
                    "reference", reference,
                    "unknownUsedAsKeyElementColumns", unknownUsedAsKeyElementColumns,
                    "knownColumns", knownColumns
            ));
        }

        public void invalidInternationalizedColumns(String reference, Set<String> unknownUsedAsKeyInternationalizedColumns, Set<String> knownColumns) {
            recordError("invalidInternationalizedColumns", ImmutableMap.of(
                    "reference", reference,
                    "unknownUsedAsInternationalizedColumns", unknownUsedAsKeyInternationalizedColumns,
                    "knownColumns", knownColumns
            ));
        }

        public void unknownUsedAsVariableComponentUniqueness(String dataType, Set<String> unknownUsedAsVariableComponentUniqueness, Set<String> availableVariableComponents) {
            recordError("unknownUsedAsVariableComponentUniqueness", ImmutableMap.of(
                    "dataType", dataType,
                    "unknownUsedAsVariableComponentUniqueness", unknownUsedAsVariableComponentUniqueness,
                    "availableVariableComponents", availableVariableComponents
            ));
        }

        public void invalidInternationalizedColumnsForDataType(String dataType, String reference, Set<String> unknownUsedAsKeyInternationalizedColumns, Set<String> knownColumns) {
            recordError("invalidInternationalizedColumnsForDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "reference", reference,
                    "unknownUsedAsInternationalizedColumns", unknownUsedAsKeyInternationalizedColumns,
                    "knownColumns", knownColumns
            ));
        }

        public void missingColumnReferenceForCheckerInReference(String validationRuleDescriptionEntryKey, Set<String> knownColumns, CheckerType name, ImmutableSet<String> missingColumns, String reference) {
            recordError("missingColumnReferenceForCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "knownColumns", knownColumns,
                    "checkerName", name,
                    "missingColumns", missingColumns
            ));
        }

        public void missingColumnReferenceForCheckerInDataType(String validationRuleDescriptionEntryKey, Set<String> knownVariableComponents, CheckerType name, ImmutableSet<String> missingVariableComponents, String dataType) {
            recordError("missingColumnReferenceForCheckerInDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "knownVariableComponents", knownVariableComponents,
                    "checkerName", name,
                    "missingVariableComponents", missingVariableComponents
            ));
        }

        public void unknownCheckerNameForVariableComponentCheckerInReference(String validationRuleDescriptionEntryKey, String reference, CheckerType name, ImmutableSet<CheckerType> checkerOnTargetNames) {
            recordError("unknownCheckerNameForVariableComponentCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "name", name,
                    "checkerOnTargetNames", checkerOnTargetNames
            ));
        }

        public void unknownCheckerNameForVariableComponentCheckerInDataType(String validationRuleDescriptionEntryKey, String dataType, CheckerType name, ImmutableSet<CheckerType> checkerOnTargetNames) {
            recordError("unknownCheckerNameForVariableComponentCheckerInDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "name", name,
                    "checkerOnTargetNames", checkerOnTargetNames
            ));
        }

        public void missingParamColumnReferenceForCheckerInReference(String validationRuleDescriptionEntryKey, String reference) {
            recordError("missingParamColumnReferenceForCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey
            ));
        }

        public void missingParamColumnReferenceForCheckerInDataType(String validationRuleDescriptionEntryKey, String dataType) {
            recordError("missingParamColumnReferenceForCheckerInDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey
            ));
        }

        public void missingAuthorizationForDatatype(String dataType) {
            recordError("missingAuthorizationForDatatype", ImmutableMap.of(
                    "datatype", dataType
            ));
        }

        public void unknownReferenceInCompositeReference(String compositeReferenceName, ImmutableSet<String> unknownReferences, Set<String> existingReferences) {
            recordError("unknownReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "unknownReferences", unknownReferences,
                    "references", existingReferences)
            );
        }

        public void missingReferenceInCompositereference(String compositeReferenceName) {
            recordError("missingReferenceInCompositereference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName)
            );
        }

        public void requiredReferenceInCompositeReferenceForParentKeyColumn(String compositeReferenceName, String parentKeyColumn) {
            recordError("requiredReferenceInCompositeReferenceForParentKeyColumn", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "parentKeyColumn", parentKeyColumn)
            );
        }

        public void requiredParentKeyColumnInCompositeReferenceForReference(String compositeReferenceName, String reference, String referenceTo) {
            recordError("requiredParentKeyColumnInCompositeReferenceForReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "referenceTo", referenceTo)
            );
        }

        public void missingParentColumnForReferenceInCompositeReference(String compositeReferenceName, String reference, String parentKeyColumn) {
            recordError("missingParentColumnForReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "parentKeyColumn", parentKeyColumn)
            );
        }

        public void missingParentRecursiveKeyColumnForReferenceInCompositeReference(String compositeReferenceName, String reference, String parentRecursiveKey) {
            recordError("missingParentRecursiveKeyColumnForReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "parentRecursiveKey", parentRecursiveKey)
            );
        }

        public void unknownReferenceInDatatypeReferenceDisplay(String dataType, String reference, Set<String> references) {
            recordError("unknownReferenceInDatatypeReferenceDisplay", ImmutableMap.of(
                    "dataType", dataType,
                    "reference", reference,
                    "references", references)
            );
        }

        public void unDeclaredValueForChart(String datatype, String variable, Set<String> components) {
            recordError("unDeclaredValueForChart", ImmutableMap.of(
                    "variable", variable,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public void missingValueComponentForChart(String datatype, String variable, String valueComponent, Set<String> components) {
            recordError("missingValueComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "valueComponent", valueComponent,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public void missingAggregationVariableForChart(String datatype, String variable, VariableComponentKey aggregation, Set<String> variables) {
            recordError("missingAggregationVariableForChart", ImmutableMap.of(
                    "variable", variable,
                    "aggregationVariable", aggregation.getVariable(),
                    "aggregationComponent", aggregation.getComponent(),
                    "dataType", datatype,
                    "variables", variables
            ));
        }

        public void missingAggregationComponentForChart(String datatype, String variable, VariableComponentKey aggregation, Set<String> components) {
            recordError("missingAggregationComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "aggregationVariable", aggregation.getVariable(),
                    "aggregationComponent", aggregation.getComponent(),
                    "dataType", datatype,
                    "components", components
            ));
        }

        public void missingStandardDeviationComponentForChart(String datatype, String variable, String standardDeviation, Set<String> components) {
            recordError("missingStandardDeviationComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "standardDeviation", standardDeviation,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public void missingUnitComponentForChart(String datatype, String variable, String unit, Set<String> components) {
            recordError("missingUnitComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "unit", unit,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public void missingKeyColumnsForReference(String reference) {
            recordError("missingKeyColumnsForReference", ImmutableMap.of(
                    "reference", reference)
            );
        }

        public void sameHeaderLineAndFirstRowLineForConstantDescription(String dataType) {
            recordError("sameHeaderLineAndFirstRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void tooBigRowLineForConstantDescription(String dataType) {
            recordError("tooBigRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void tooLittleRowLineForConstantDescription(String dataType) {
            recordError("tooLittleRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void missingRowLineForConstantDescription(String dataType) {
            recordError("missingRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void missingColumnNumberOrHeaderNameForConstantDescription(String dataType) {
            recordError("missingColumnNumberOrHeaderNameForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void missingBoundToForConstantDescription(String dataType) {
            recordError("missingBoundToForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void missingExportHeaderNameForConstantDescription(String dataType) {
            recordError("missingExportHeaderNameForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void unknownReferenceForCheckerInReferenceColumn(String referenceToValidate, String column, String refType, Set<String> knownReferences) {
            recordError("unknownReferenceForCheckerInReferenceColumn", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "refType", refType,
                    "knownReferences", knownReferences
            ));
        }

        public void missingReferenceForCheckerInReferenceColumn(String referenceToValidate, String column, Set<String> knownReferences) {
            recordError("missingReferenceForCheckerInReferenceColumn", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "knownReferences", knownReferences
            ));
        }

        public void unknownCheckerNameInReferenceColumn(String referenceToValidate, String column, CheckerType checkerName, ImmutableSet<CheckerType> knownCheckerNames) {
            recordError("unknownCheckerNameInReferenceColumn", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "checkerName", checkerName,
                    "knownCheckerNames", knownCheckerNames
            ));
        }

        public void invalidPatternForVariableComponentDateChecker(String dataType, String variable, String component, String pattern) {
            recordError("invalidPatternForVariableComponentDateChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "component", component,
                    "pattern", pattern
            ));
        }

        public void invalidPatternForReferenceColumnDateChecker(String referenceToValidate, String column, String pattern) {
            recordError("invalidPatternForReferenceColumnDateChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "pattern", pattern
            ));
        }

        public void invalidPatternForDateCheckerForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, String pattern) {
            recordError("invalidPatternForDateCheckerForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "pattern", pattern
            ));
        }

        public void invalidPatternForDateCheckerForValidationRuleInReference(String validationRuleDescriptionEntryKey, String reference, String pattern) {
            recordError("invalidPatternForDateCheckerForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "reference", reference,
                    "pattern", pattern
            ));
        }

        public void invalidDurationForVariableComponentDateChecker(String dataType, String variable, String component, String duration) {
            recordError("invalidDurationForVariableComponentDateChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "component", component,
                    "duration", duration
            ));
        }

        public void invalidDurationForReferenceColumnDateChecker(String referenceToValidate, String column, String duration) {
            recordError("invalidDurationForReferenceColumnDateChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "duration", duration
            ));
        }

        public void invalidDurationForDateCheckerForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, String duration) {
            recordError("invalidDurationForDateCheckerForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "duration", duration
            ));
        }

        public void invalidDurationForDateCheckerForValidationRuleInReference(String validationRuleDescriptionEntryKey, String reference, String duration) {
            recordError("invalidDurationForDateCheckerForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "reference", reference,
                    "duration", duration
            ));
        }

        public void invalidPatternForVariableComponentRegularExpressionChecker(String dataType, String variable, String component, String pattern) {
            recordError("invalidPatternForVariableComponentRegularExpressionChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "component", component,
                    "pattern", pattern
            ));
        }

        public void invalidPatternForReferenceColumnRegularExpressionChecker(String referenceToValidate, String column, String pattern) {
            recordError("invalidPatternForReferenceColumnRegularExpressionChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "pattern", pattern
            ));
        }

        public void invalidPatternForRegularExpressionCheckerForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, String pattern) {
            recordError("invalidPatternForRegularExpressionCheckerForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "pattern", pattern
            ));
        }

        public void invalidPatternForRegularExpressionCheckerForValidationRuleInReference(String validationRuleDescriptionEntryKey, String reference, String pattern) {
            recordError("invalidPatternForRegularExpressionCheckerForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "reference", reference,
                    "pattern", pattern
            ));
        }

        public void illegalCheckerConfigurationParameterForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, CheckerType checkerName, String parameterName) {
            recordError("illegalCheckerConfigurationParameterForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "checkerName", checkerName,
                    "parameterName", parameterName
            ));
        }

        public void illegalCheckerConfigurationParameterForValidationRuleInReference(String validationRuleDescriptionEntryKey, String referenceToValidate, CheckerType checkerName, String parameterName) {
            recordError("illegalCheckerConfigurationParameterForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "referenceToValidate", referenceToValidate,
                    "checkerName", checkerName,
                    "parameterName", parameterName
            ));
        }

        public void illegalCheckerConfigurationParameterForVariableComponentChecker(String dataType, String datum, String component, CheckerType checkerName, String parameterName) {
            recordError("illegalCheckerConfigurationParameterForVariableComponentChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "datum", datum,
                    "component", component,
                    "checkerName", checkerName,
                    "parameterName", parameterName
            ));
        }

        public void illegalCheckerConfigurationParameterForReferenceColumnChecker(String referenceToValidate, String column, CheckerType checkerName, String parameterName) {
            recordError("illegalCheckerConfigurationParameterForReferenceColumnChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "checkerName", checkerName,
                    "parameterName", parameterName
            ));
        }

        public void authorizationScopeMissingReferenceCheckerForAuthorizationScope(Map.Entry<String, Configuration.AuthorizationScopeDescription> authorizationScopeVariableComponentKeyEntry, String dataType) {
            recordError("authorizationScopeMissingReferenceCheckerForAuthorizationScope", ImmutableMap.of(
                    "authorizationScopeName", authorizationScopeVariableComponentKeyEntry.getKey(),
                    "variable", authorizationScopeVariableComponentKeyEntry.getValue().getVariable(),
                    "component", authorizationScopeVariableComponentKeyEntry.getValue().getComponent(),
                    "dataType", dataType
            ));
        }

        public void noCapturingGroupForDatatypeRepository(String dataType) {
            recordError("noCapturingGroupForDatatypeRepository", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void invalidPatternForDatatypeRepository(String dataType) {
            recordError("invalidPatternForDatatypeRepository", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public void invalidCapturingGroupForDatatypeRepositoryAuthorizationScope(String dataType, Integer scopeToken, long countGroups, String scopeName) {

            recordError("invalidCapturingGroupForDatatypeRepositoryAuthorizationScope", ImmutableMap.of(
                    "scopeName", scopeName,
                    "scopeToken", scopeToken,
                    "countGroups", countGroups,
                    "dataType", dataType
            ));
        }

        public void invalidCapturingGroupForDatatypeRepository(String dataType, Map<String, Object> messages) {
            final Object scopeName = messages.get("scopeName");
            final Object registerScopes = messages.get("registerScopes");
            recordError("invalidCapturingGroupForDatatypeRepository", ImmutableMap.of(
                    "scopeName", scopeName,
                    "registerScopes", registerScopes,
                    "dataType", dataType
            ));
        }

        public void invalidCapturingGroupForDatatypeRepositoryDate(String dataType, Integer token, long countGroups, boolean isStart) {
            String message;
            if (isStart) {
                message = "invalidCapturingGroupForStartDateDatatypeRepositoryDate";
            } else {
                message = "invalidCapturingGroupForEndDateDatatypeRepositoryDate";
            }
            recordError(message, ImmutableMap.of(
                    "token", token,
                    "countGroups", countGroups,
                    "dataType", dataType
            ));
        }

        public void missingTagDeclaration(String sectionName, String variableName, String compenentName, String tagName, Set<String> declaredTags, String message) {
            recordError(message,ImmutableMap.of(
                    "sectionName",sectionName,
                    "variableName",variableName,
                    "compenentName",compenentName,
                    "tagName",tagName,
                    "declaredTag",declaredTags)
            );
        }

        public void missingTagDeclaration(String sectionName, String variableName, String tagName, Set<String> declaredTags, String message) {
            recordError(message,ImmutableMap.of(
                    "sectionName",sectionName,
                    "variableName",variableName,
                    "tagName",tagName,
                    "declaredTag",declaredTags)
            );
        }

        public void missingTagDeclaration(String sectionName, String tagName, Set<String> declaredTags, String message) {
            recordError(message,ImmutableMap.of(
                    "sectionName",sectionName,
                    "tagName",tagName,
                    "declaredTag",declaredTags)
            );
        }
    }
}