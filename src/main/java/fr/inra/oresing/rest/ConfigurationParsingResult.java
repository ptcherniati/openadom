package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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

        public Builder missingReferenceForCheckerInDataType(String validationKey, String dataType, Set<String> references)  {
            return recordError("missingReferenceForCheckerInDataType", ImmutableMap.of(
                    "validationKey", validationKey,
                    "dataType", dataType,
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

        public Builder unknownReferenceForCheckerInDataType(String validationKey, String dataType, String refType, Set<String> references) {
            return recordError("unknownReferenceForCheckerInDataType", ImmutableMap.of(
                    "validationKey", validationKey,
                    "refType", refType,
                    "dataType", dataType,
                    "references", references));
        }

        public Builder unknownReferenceForChecker(String dataType, String datum, String component, String refType, Set<String> references) {
            return recordError("unknownReferenceForChecker", ImmutableMap.of("dataType", dataType,
                    "datum", datum,
                    "refType", refType,
                    "component", component,
                    "references", references));
        }

        public Builder undeclaredDataGroupForVariable(String variable) {
            return recordError("undeclaredDataGroupForVariable", ImmutableMap.of("variable", variable));
        }

        public Builder variableInMultipleDataGroup(String variable) {
            return recordError("variableInMultipleDataGroup", ImmutableMap.of("variable", variable));
        }

        public Builder unknownVariablesInDataGroup(String dataGroup, Set<String> unknownVariables, Set<String> variables) {
            return recordError("unknownVariablesInDataGroup", ImmutableMap.of(
                    "dataGroup", dataGroup,
                    "unknownVariables", unknownVariables,
                    "variables", variables)
            );
        }

        public Builder missingTimeScopeVariableComponentKey(String dataType) {
            return recordError("missingTimeScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public Builder missingAuthorizationScopeVariableComponentKey(String dataType) {
            return recordError("missingAuthorizationScopeVariableComponentKey", ImmutableMap.of("dataType", dataType));
        }

        public Builder timeScopeVariableComponentKeyMissingVariable(String dataType, Set<String> variables) {
            return recordError("timeScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "variables", variables));
        }

        public Builder authorizationScopeVariableComponentKeyMissingVariable(String dataType, String authorizationScopeName, Set<String> variables) {
            return recordError("authorizationScopeVariableComponentKeyMissingVariable", ImmutableMap.of("dataType", dataType, "authorizationScopeName", authorizationScopeName, "variables", variables));
        }

        public Builder timeScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            return recordError("timeScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public Builder authorizationScopeVariableComponentKeyUnknownVariable(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownVariables) {
            return recordError("authorizationScopeVariableComponentKeyUnknownVariable", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "knownVariables", knownVariables));
        }

        public Builder timeVariableComponentKeyMissingComponent(String dataType, String variable, Set<String> knownComponents) {
            return recordError("timeVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public Builder authorizationVariableComponentKeyMissingComponent(String dataType, String authorizationName, String variable, Set<String> knownComponents) {
            return recordError("authorizationVariableComponentKeyMissingComponent", ImmutableMap.of(
                    "dataType", dataType,
                    "authorizationName", authorizationName,
                    "variable", variable,
                    "knownComponents", knownComponents
            ));
        }

        public Builder timeVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            return recordError("timeVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public Builder authorizationVariableComponentKeyUnknownComponent(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownComponents) {
            return recordError("authorizationVariableComponentKeyUnknownComponent", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownComponents", knownComponents));
        }

        public Builder timeScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            return recordError("timeScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public Builder authorizationScopeVariableComponentWrongChecker(VariableComponentKey timeScopeVariableComponentKey, String expectedChecker) {
            return recordError("authorizationScopeVariableComponentWrongChecker", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "expectedChecker", expectedChecker));
        }

        public Builder timeScopeVariableComponentPatternUnknown(VariableComponentKey timeScopeVariableComponentKey, String pattern, Set<String> knownPatterns) {
            return recordError("timeScopeVariableComponentPatternUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "pattern", pattern, "knownPatterns", knownPatterns));
        }

        public Builder authorizationScopeVariableComponentReftypeUnknown(VariableComponentKey timeScopeVariableComponentKey, String refType, Set<String> knownPatterns) {
            return recordError("authorizationScopeVariableComponentReftypeUnknown", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "refType", refType, "knownPatterns", knownPatterns));
        }

        public Builder authorizationScopeVariableComponentReftypeNull(VariableComponentKey timeScopeVariableComponentKey, Set<String> knownPatterns) {
            return recordError("authorizationScopeVariableComponentReftypeNull", ImmutableMap.of("variable", timeScopeVariableComponentKey.getVariable(), "component", timeScopeVariableComponentKey.getComponent(), "knownPatterns", knownPatterns));
        }

        public Builder authorizationScopeVariableComponentReftypeUnknown(String dataType, String authorizationName, String refType, Set<String> knownCompositesReferences) {
            return recordError("authorizationScopeVariableComponentReftypeUnknown", ImmutableMap.of("dataType", dataType, "authorizationName", authorizationName, "refType", refType, "knownCompositesReferences", knownCompositesReferences));
        }

        public Builder unrecognizedProperty(int lineNumber, int columnNumber, String unknownPropertyName, Collection<String> knownProperties) {
            return recordError("unrecognizedProperty", ImmutableMap.of(
                    "lineNumber", lineNumber,
                    "columnNumber", columnNumber,
                    "unknownPropertyName", unknownPropertyName,
                    "knownProperties", knownProperties
            ));
        }

        public Builder invalidFormat(int lineNumber, int columnNumber, String value, String targetTypeName) {
            return recordError("invalidFormat", ImmutableMap.of(
                    "lineNumber", lineNumber,
                    "columnNumber", columnNumber,
                    "value", value,
                    "targetTypeName", targetTypeName
            ));
        }

        public Builder missingRequiredExpressionForValidationRuleInDataType(String lineValidationRuleKey, String dataType) {
            return recordError("missingRequiredExpressionForValidationRuleInDataType", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "dataType", dataType
            ));
        }

        public Builder missingRequiredExpressionForValidationRuleInReference(String lineValidationRuleKey, String reference) {
            return recordError("missingRequiredExpressionForValidationRuleInReference", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "reference", reference
            ));
        }

        public Builder illegalGroovyExpressionForValidationRuleInDataType(String lineValidationRuleKey, String dataType, String expression, GroovyExpression.CompilationError compilationError) {
            return recordError("illegalGroovyExpressionForValidationRuleInDataType", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "dataType", dataType,
                    "expression", expression,
                    "compilationError", compilationError
            ));
        }

        public Builder illegalGroovyExpressionForValidationRuleInReference(String lineValidationRuleKey, String reference, String expression, GroovyExpression.CompilationError compilationError) {
            return recordError("illegalGroovyExpressionForValidationRuleInReference", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "reference", reference,
                    "expression", expression,
                    "compilationError", compilationError
            ));
        }

        public Builder unknownCheckerNameForValidationRuleInReference(String lineValidationRuleKey, String reference, String checkerName, ImmutableSet<String> allCheckerNames) {
            return recordError("unknownCheckerNameForValidationRuleInReference", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "reference", reference,
                    "allCheckerNames", allCheckerNames,
                    "checkerName", checkerName
            ));
        }

        public Builder unknownCheckerNameForValidationRuleInDataType(String lineValidationRuleKey, String dataType, String checkerName, ImmutableSet<String> allCheckerNames) {
            return recordError("unknownCheckerNameForValidationRuleInDataType", ImmutableMap.of(
                    "lineValidationRuleKey", lineValidationRuleKey,
                    "dataType", dataType,
                    "allCheckerNames", allCheckerNames,
                    "checkerName", checkerName
            ));
        }

        public Builder unknownCheckerNameForVariableComponent(String dataType, String variable, String component, String checkerName, ImmutableSet<String> knownCheckerNames) {
            return recordError("unknownCheckerNameForVariableComponent", ImmutableMap.of(
                    "datatype", dataType,
                    "variable", variable,
                    "component", component,
                    "checkerName", checkerName,
                    "knownCheckerNames", knownCheckerNames
            ));
        }

        public Builder csvBoundToUnknownVariable(String header, String variable, Set<String> variables) {
            return recordError("csvBoundToUnknownVariable", ImmutableMap.of(
                    "header", header,
                    "variable", variable,
                    "variables", variables
            ));
        }

        public Builder csvBoundToUnknownVariableComponent(String header, String variable, String component, Set<String> components) {
            return recordError("csvBoundToUnknownVariableComponent", ImmutableMap.of(
                    "header", header,
                    "variable", variable,
                    "component", component,
                    "components", components
            ));
        }

        public Builder invalidKeyColumns(String reference, Set<String> unknownUsedAsKeyElementColumns, Set<String> knownColumns) {
            return recordError("invalidKeyColumns", ImmutableMap.of(
                    "reference", reference,
                    "unknownUsedAsKeyElementColumns", unknownUsedAsKeyElementColumns,
                    "knownColumns", knownColumns
            ));
        }

        public Builder invalidInternationalizedColumns(String reference, Set<String> unknownUsedAsKeyInternationalizedColumns, Set<String> knownColumns) {
            return recordError("invalidInternationalizedColumns", ImmutableMap.of(
                    "reference", reference,
                    "unknownUsedAsInternationalizedColumns", unknownUsedAsKeyInternationalizedColumns,
                    "knownColumns", knownColumns
            ));
        }

        public Builder unknownUsedAsVariableComponentUniqueness(String dataType, Set<String> unknownUsedAsVariableComponentUniqueness,Set<String>  availableVariableComponents) {
            return recordError("unknownUsedAsVariableComponentUniqueness", ImmutableMap.of(
                    "dataType", dataType,
                    "unknownUsedAsVariableComponentUniqueness", unknownUsedAsVariableComponentUniqueness,
                    "availableVariableComponents", availableVariableComponents
            ));
        }

        public Builder invalidInternationalizedColumnsForDataType(String dataType, String reference, Set<String> unknownUsedAsKeyInternationalizedColumns, Set<String> knownColumns) {
            return recordError("invalidInternationalizedColumnsForDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "reference", reference,
                    "unknownUsedAsInternationalizedColumns", unknownUsedAsKeyInternationalizedColumns,
                    "knownColumns", knownColumns
            ));
        }

        public Builder missingColumnReferenceForCheckerInReference(String validationRuleDescriptionEntryKey, Set<String> knownColumns, String name, ImmutableSet<String> missingColumns, String reference) {
            return recordError("missingColumnReferenceForCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "knownColumns", knownColumns,
                    "checkerName", name,
                    "missingColumns", missingColumns
            ));
        }

        public Builder missingColumnReferenceForCheckerInDataType(String validationRuleDescriptionEntryKey, Set<String> knownVariableComponents, String name, ImmutableSet<String> missingVariableComponents, String dataType) {
            return recordError("missingColumnReferenceForCheckerInDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "knownVariableComponents", knownVariableComponents,
                    "checkerName", name,
                    "missingVariableComponents", missingVariableComponents
            ));
        }

        public Builder unknownCheckerNameForVariableComponentCheckerInReference(String validationRuleDescriptionEntryKey, String reference, String name, ImmutableSet<String> checkerOnTargetNames) {
            return recordError("unknownCheckerNameForVariableComponentCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "name", name,
                    "checkerOnTargetNames", checkerOnTargetNames
            ));
        }

        public Builder unknownCheckerNameForVariableComponentCheckerInDataType(String validationRuleDescriptionEntryKey, String dataType, String name, ImmutableSet<String> checkerOnTargetNames) {
            return recordError("unknownCheckerNameForVariableComponentCheckerInDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "name", name,
                    "checkerOnTargetNames", checkerOnTargetNames
            ));
        }

        public Builder missingParamColumnReferenceForCheckerInReference(String validationRuleDescriptionEntryKey, String reference) {
            return recordError("missingParamColumnReferenceForCheckerInReference", ImmutableMap.of(
                    "reference", reference,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey
            ));
        }

        public Builder missingParamColumnReferenceForCheckerInDataType(String validationRuleDescriptionEntryKey, String dataType) {
            return recordError("missingParamColumnReferenceForCheckerInDataType", ImmutableMap.of(
                    "dataType", dataType,
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey
            ));
        }

        public Builder missingAuthorizationForDatatype(String dataType) {
            return recordError("missingAuthorizationForDatatype", ImmutableMap.of(
                    "datatype", dataType
            ));
        }

        public Builder unknownReferenceInCompositeReference(String compositeReferenceName, ImmutableSet<String> unknownReferences, Set<String> existingReferences) {
            return recordError("unknownReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "unknownReferences", unknownReferences,
                    "references", existingReferences)
            );
        }

        public Builder missingReferenceInCompositereference(String compositeReferenceName) {
            return recordError("missingReferenceInCompositereference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName)
            );
        }

        public Builder requiredReferenceInCompositeReferenceForParentKeyColumn(String compositeReferenceName, String parentKeyColumn) {
            return recordError("requiredReferenceInCompositeReferenceForParentKeyColumn", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "parentKeyColumn", parentKeyColumn)
            );
        }

        public Builder requiredParentKeyColumnInCompositeReferenceForReference(String compositeReferenceName, String reference, String referenceTo) {
            return recordError("requiredParentKeyColumnInCompositeReferenceForReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "referenceTo", referenceTo)
            );
        }

        public Builder missingParentColumnForReferenceInCompositeReference(String compositeReferenceName, String reference, String parentKeyColumn) {
            return recordError("missingParentColumnForReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "parentKeyColumn", parentKeyColumn)
            );
        }

        public Builder missingParentRecursiveKeyColumnForReferenceInCompositeReference(String compositeReferenceName, String reference, String parentRecursiveKey) {
            return recordError("missingParentRecursiveKeyColumnForReferenceInCompositeReference", ImmutableMap.of(
                    "compositeReference", compositeReferenceName,
                    "reference", reference,
                    "parentRecursiveKey", parentRecursiveKey)
            );
        }

        public Builder unknownReferenceInDatatypeReferenceDisplay(String dataType, String reference, Set<String> references) {
            return recordError("unknownReferenceInDatatypeReferenceDisplay", ImmutableMap.of(
                    "dataType", dataType,
                    "reference", reference,
                    "references", references)
            );
        }

        public Builder unDeclaredValueForChart(String datatype, String variable, Set<String> components) {
            return recordError("unDeclaredValueForChart", ImmutableMap.of(
                    "variable", variable,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public Builder missingValueComponentForChart(String datatype, String variable, String valueComponent, Set<String> components) {
            return recordError("missingValueComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "valueComponent", valueComponent,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public Builder missingAggregationVariableForChart(String datatype, String variable, VariableComponentKey aggregation, Set<String> variables) {
            return recordError("missingAggregationVariableForChart", ImmutableMap.of(
                    "variable", variable,
                    "aggregationVariable", aggregation.getVariable(),
                    "aggregationComponent", aggregation.getComponent(),
                    "dataType", datatype,
                    "variables",variables
            ));
        }

        public Builder missingAggregationComponentForChart(String datatype, String variable, VariableComponentKey aggregation, Set<String> components) {
            return recordError("missingAggregationComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "aggregationVariable", aggregation.getVariable(),
                    "aggregationComponent", aggregation.getComponent(),
                    "dataType", datatype,
                    "components", components
            ));
        }

        public Builder missingStandardDeviationComponentForChart(String datatype, String variable, String standardDeviation, Set<String> components) {
            return recordError("missingStandardDeviationComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "standardDeviation",standardDeviation,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public Builder missingUnitComponentForChart(String datatype, String variable, String unit, Set<String> components) {
            return recordError("missingUnitComponentForChart", ImmutableMap.of(
                    "variable", variable,
                    "unit",unit,
                    "dataType", datatype,
                    "components", components
            ));
        }

        public Builder missingKeyColumnsForReference(String reference) {
            return recordError("missingKeyColumnsForReference", ImmutableMap.of(
                    "reference", reference)
            );
        }

        public Builder sameHeaderLineAndFirstRowLineForConstantDescription(String dataType) {
            return recordError("sameHeaderLineAndFirstRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder tooBigRowLineForConstantDescription(String dataType) {
            return recordError("tooBigRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder tooLittleRowLineForConstantDescription(String dataType) {
            return recordError("tooLittleRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder missingRowLineForConstantDescription(String dataType) {
            return recordError("missingRowLineForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder missingColumnNumberOrHeaderNameForConstantDescription(String dataType) {
            return recordError("missingColumnNumberOrHeaderNameForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder missingBoundToForConstantDescription(String dataType) {
            return recordError("missingBoundToForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder missingExportHeaderNameForConstantDescription(String dataType) {
            return recordError("missingExportHeaderNameForConstantDescription", ImmutableMap.of(
                    "dataType", dataType
            ));
        }

        public Builder unknownReferenceForCheckerInReferenceColumn(String referenceToValidate, String column, String refType, Set<String> knownReferences) {
            return recordError("unknownReferenceForCheckerInReferenceColumn", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "refType", refType,
                    "knownReferences", knownReferences
            ));
        }

        public Builder missingReferenceForCheckerInReferenceColumn(String referenceToValidate, String column, Set<String> knownReferences) {
            return recordError("missingReferenceForCheckerInReferenceColumn", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "knownReferences", knownReferences
            ));
        }

        public Builder unknownCheckerNameInReferenceColumn(String referenceToValidate, String column, String checkerName, ImmutableSet<String> knownCheckerNames) {
            return recordError("unknownCheckerNameInReferenceColumn", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "checkerName", checkerName,
                    "knownCheckerNames", knownCheckerNames
            ));
        }

        public Builder invalidPatternForVariableComponentDateChecker(String dataType, String variable, String component, String pattern) {
            return recordError("invalidPatternForVariableComponentDateChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "component", component,
                    "pattern", pattern
            ));
        }

        public Builder invalidPatternForReferenceColumnDateChecker(String referenceToValidate, String column, String pattern) {
            return recordError("invalidPatternForReferenceColumnDateChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "pattern", pattern
            ));
        }

        public Builder invalidPatternForDateCheckerForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, String pattern) {
            return recordError("invalidPatternForDateCheckerForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "pattern", pattern
            ));
        }

        public Builder invalidPatternForDateCheckerForValidationRuleInReference(String validationRuleDescriptionEntryKey, String reference, String pattern) {
            return recordError("invalidPatternForDateCheckerForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "reference", reference,
                    "pattern", pattern
            ));
        }

        public Builder invalidDurationForVariableComponentDateChecker(String dataType, String variable, String component, String duration) {
            return recordError("invalidDurationForVariableComponentDateChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "component", component,
                    "duration", duration
            ));
        }

        public Builder invalidDurationForReferenceColumnDateChecker(String referenceToValidate, String column, String duration) {
            return recordError("invalidDurationForReferenceColumnDateChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "duration", duration
            ));
        }

        public Builder invalidDurationForDateCheckerForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, String duration) {
            return recordError("invalidDurationForDateCheckerForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "duration", duration
            ));
        }

        public Builder invalidDurationForDateCheckerForValidationRuleInReference(String validationRuleDescriptionEntryKey, String reference, String duration) {
            return recordError("invalidDurationForDateCheckerForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "reference", reference,
                    "duration", duration
            ));
        }
        
        public Builder invalidPatternForVariableComponentRegularExpressionChecker(String dataType, String variable, String component, String pattern) {
            return recordError("invalidPatternForVariableComponentRegularExpressionChecker", ImmutableMap.of(
                    "dataType", dataType,
                    "variable", variable,
                    "component", component,
                    "pattern", pattern
            ));
        }

        public Builder invalidPatternForReferenceColumnRegularExpressionChecker(String referenceToValidate, String column, String pattern) {
            return recordError("invalidPatternForReferenceColumnRegularExpressionChecker", ImmutableMap.of(
                    "referenceToValidate", referenceToValidate,
                    "column", column,
                    "pattern", pattern
            ));
        }

        public Builder invalidPatternForRegularExpressionCheckerForValidationRuleInDataType(String validationRuleDescriptionEntryKey, String dataType, String pattern) {
            return recordError("invalidPatternForRegularExpressionCheckerForValidationRuleInDataType", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "dataType", dataType,
                    "pattern", pattern
            ));
        }

        public Builder invalidPatternForRegularExpressionCheckerForValidationRuleInReference(String validationRuleDescriptionEntryKey, String reference, String pattern) {
            return recordError("invalidPatternForRegularExpressionCheckerForValidationRuleInReference", ImmutableMap.of(
                    "validationRuleDescriptionEntryKey", validationRuleDescriptionEntryKey,
                    "reference", reference,
                    "pattern", pattern
            ));
        }
    }
}
