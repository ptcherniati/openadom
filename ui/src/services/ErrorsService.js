import { i18n } from "@/main";
//prettier-ignore

const ERRORS = {
  emptyFile: () => i18n.t("errors.emptyFile"),
  missingReferenceForChecker: (params) => i18n.t("errors.missingReferenceForChecker", params),
  unknownReferenceForChecker: (params) => i18n.t("errors.unknownReferenceForChecker", params),
  unsupportedVersion: (params) => i18n.t("errors.unsupportedVersion", params),
  undeclaredDataGroupForVariable: (params) => i18n.t("errors.undeclaredDataGroupForVariable", params),
  variableInMultipleDataGroup: (params) => i18n.t("errors.variableInMultipleDataGroup", params),
  unknownVariablesInDataGroup: (params) => i18n.t("errors.unknownVariablesInDataGroup", params),
  missingTimeScopeVariableComponentKey: (params) => i18n.t("errors.missingTimeScopeVariableComponentKey", params),
  timeScopeVariableComponentKeyMissingVariable: (params) => i18n.t("errors.timeScopeVariableComponentKeyMissingVariable", params),
  timeScopeVariableComponentKeyUnknownVariable: (params) => i18n.t("errors.timeScopeVariableComponentKeyUnknownVariable", params),
  timeVariableComponentKeyMissingComponent: (params) => i18n.t("errors.timeVariableComponentKeyMissingComponent", params),
  timeVariableComponentKeyUnknownComponent: (params) => i18n.t("errors.timeVariableComponentKeyUnknownComponent", params),
  timeScopeVariableComponentWrongChecker: (params) => i18n.t("errors.timeScopeVariableComponentWrongChecker", params),
  timeScopeVariableComponentPatternUnknown: (params) => i18n.t("errors.timeScopeVariableComponentPatternUnknown", params),
  unrecognizedProperty: (params) => i18n.t("errors.unrecognizedProperty", params),
  invalidFormat: (params) => i18n.t("errors.invalidFormat", params),
  missingRequiredExpression: (params) => i18n.t("errors.missingRequiredExpression", params),
  illegalGroovyExpression: (params) => i18n.t("errors.illegalGroovyExpression", params),
  unknownCheckerName: (params) => i18n.t("errors.unknownCheckerName", params),
  csvBoundToUnknownVariable: (params) => i18n.t("errors.csvBoundToUnknownVariable", params),
  csvBoundToUnknownVariableComponent: (params) => i18n.t("errors.csvBoundToUnknownVariableComponent", params),
  invalidKeyColumns: (params) => i18n.t("errors.invalidKeyColumns", params),
  invalidInternationalizedColumns: (params) => i18n.t("errors.invalidInternationalizedColumns", params),
  unexpectedHeaderColumn : (params) => i18n.t("errors.unexpectedHeaderColumn", params),
  headerColumnPatternNotMatching :(params) => i18n.t("errors.headerColumnPatternNotMatching", params),
  unexpectedTokenCount : (params) => i18n.t("errors.unexpectedTokenCount", params),
  invalidHeaders : (params) => i18n.t("errors.invalidHeaders", params),
  emptyHeader : (params) => i18n.t("errors.emptyHeader", params),
  duplicatedHeaders : (params) => i18n.t("errors.duplicatedHeaders", params),
  patternNotMatched : (params) => i18n.t("errors.patternNotMatched", params),
  invalidDate : (params) => i18n.t("errors.invalidDate", params),
  invalidInteger : (params) => i18n.t("errors.invalidInteger", params),
  invalidFloat : (params) => i18n.t("errors.invalidFloat", params),
  checkerExpressionReturnedFalse : (params) => i18n.t("errors.checkerExpressionReturnedFalse", params),
  missingReferenceForCheckerInReference: (params) => i18n.t("errors.missingReferenceForCheckerInReference", params),
  unknownReferenceForCheckerInReference: (params) => i18n.t("errors.unknownReferenceForCheckerInReference", params),
  unknownReferenceInCompositereference: (params) => i18n.t("errors.unknownReferenceInCompositereference", params),
  missingReferenceInCompositereference: (params) => i18n.t("errors.missingReferenceInCompositereference", params),
  requiredReferenceInCompositeReferenceForParentKeyColumn: (params) => i18n.t("errors.requiredReferenceInCompositeReferenceForParentKeyColumn", params),
  requiredParentKeyColumnInCompositeReferenceForReference: (params) => i18n.t("errors.requiredParentKeyColumnInCompositeReferenceForReference", params),
  missingParentColumnForReferenceInCompositeReference: (params) => i18n.t("errors.missingParentColumnForReferenceInCompositeReference", params),
  missingParentRecursiveKeyColumnForReferenceInCompositeReference: (params) => i18n.t("errors.missingParentRecursiveKeyColumnForReferenceInCompositeReference", params),
  missingAuthorizationScopeVariableComponentKey: (params) => i18n.t("errors.missingAuthorizationScopeVariableComponentKey", params),
  missingAuthorizationForDatatype: (params) => i18n.t("errors.missingAuthorizationForDatatype", params),
  authorizationScopeVariableComponentKeyMissingVariable:  (params) => i18n.t("errors.authorizationScopeVariableComponentKeyMissingVariable", params),
  authorizationScopeVariableComponentKeyUnknownVariable:  (params) => i18n.t("errors.authorizationScopeVariableComponentKeyUnknownVariable", params),
  authorizationVariableComponentKeyMissingComponent: (params) => i18n.t("errors.authorizationVariableComponentKeyMissingComponent", params),
  authorizationVariableComponentKeyUnknownComponent:  (params) => i18n.t("errors.authorizationVariableComponentKeyUnknownComponent", params),
  authorizationScopeVariableComponentWrongChecker: (params) => i18n.t("errors.authorizationScopeVariableComponentWrongChecker", params),
  authorizationScopeVariableComponentReftypeUnknown: (params) => i18n.t("errors.authorizationScopeVariableComponentReftypeUnknown", params),
  authorizationScopeVariableComponentReftypeNull: (params) => i18n.t("errors.authorizationScopeVariableComponentReftypeNull", params),
  authorizationVariableComponentMustReferToCompositereference: (params) => i18n.t("errors.authorizationVariableComponentMustReferToCompositereference", params),
  unknownCheckerNameForVariableComponentCheckerInReference: (params) => i18n.t("errors.unknownCheckerNameForVariableComponentCheckerInReference", params),
  unknownCheckerNameForVariableComponent: (params) => i18n.t("errors.unknownCheckerNameForVariableComponent", params),
  missingColumnReferenceForCheckerInReference: (params) => i18n.t("errors.missingColumnReferenceForCheckerInReference", params),
  missingParamColumnReferenceForCheckerInReference: (params) => i18n.t("errors.missingParamColumnReferenceForCheckerInReference", params),
  missingKeyColumnsForReference: (params) => i18n.t("errors.missingKeyColumnsForReference", params),
  invalidInternationalizedColumnsForDataType: (params) => i18n.t("errors.invalidInternationalizedColumnsForDataType", params),
  unknownReferenceInDatatypeReferenceDisplay: (params) => i18n.t("errors.unknownReferenceInDatatypeReferenceDisplay", params),
  patternNotMatchedWithColumn: (params) => i18n.t("errors.patternNotMatchedWithColumn", params),
  invalidDateWithColumn: (params) => i18n.t("errors.invalidDateWithColumn", params),
  invalidIntegerWithColumn: (params) => i18n.t("errors.invalidIntegerWithColumn", params),
  invalidFloatWithColumn: (params) => i18n.t("errors.invalidFloatWithColumn", params),
  invalidReference: (params) => i18n.t("errors.invalidReference", params),
  invalidReferenceWithColumn: (params) => i18n.t("errors.invalidReferenceWithColumn", params),
  requiredValue: (params) => i18n.t("errors.requiredValue", params),
  requiredValueWithColumn: (params) => i18n.t("errors.requiredValueWithColumn", params),
  timerangeoutofinterval: (params) => i18n.t("errors.timerangeoutofinterval", params),
  badauthorizationscopeforrepository: (params) => i18n.t("errors.badauthorizationscopeforrepository", params),
  overlappingpublishedversion: (params) => i18n.t("errors.overlappingpublishedversion", params),
  unDeclaredValueForChart: (params) => i18n.t("errors.unDeclaredValueForChart", params),
  missingValueComponentForChart: (params) => i18n.t("errors.missingValueComponentForChart", params),
  missingAggregationVariableForChart: (params) => i18n.t("errors.missingAggregationVariableForChart", params),
  missingAggregationComponentForChart: (params) => i18n.t("errors.missingAggregationComponentForChart", params),
  missingStandardDeviationComponentForChart: (params) => i18n.t("errors.missingStandardDeviationComponentForChart", params),
  missingUnitComponentForChart: (params) => i18n.t("errors.missingUnitComponentForChart", params),
  duplicatedLineInReference: (params) => i18n.t("errors.duplicatedLineInReference", params),
  duplicatedLineInDatatype: (params) => i18n.t("errors.duplicatedLineInDatatype", params),
  missingParentLineInRecursiveReference: (params) => i18n.t("errors.missingParentLineInRecursiveReference", params),
  unknownUsedAsVariableComponentUniqueness: (params) => i18n.t("errors.unknownUsedAsVariableComponentUniqueness", params),
  tooBigRowLineForConstantDescription: (params) => i18n.t("errors.tooBigRowLineForConstantDescription", params),
  tooLittleRowLineForConstantDescription: (params) => i18n.t("errors.tooLittleRowLineForConstantDescription", params),
  missingRowLineForConstantDescription: (params) => i18n.t("errors.missingRowLineForConstantDescription", params),
  recordCsvMissingColumnNumberOrHeaderNameForConstantDescription: (params) => i18n.t("errors.recordCsvMissingColumnNumberOrHeaderNameForConstantDescription", params),
  missingBoundToForConstantDescription: (params) => i18n.t("errors.missingBoundToForConstantDescription", params),
  missingExportHeaderNameForConstantDescription: (params) => i18n.t("errors.missingExportHeaderNameForConstantDescription", params),
  sameHeaderLineAndFirstRowLineForConstantDescription: (params) => i18n.t("errors.sameHeaderLineAndFirstRowLineForConstantDescription", params)
};

export class ErrorsService {
  static INSTANCE = new ErrorsService();

  getErrorsMessages(errors) {
    return errors.map((error) => {
      const func = ERRORS[error.message];
      if (!func) {
        throw new Error("Il manque la chaine de traduction pour l'erreur : " + error.message);
      }
      return func(error.messageParams);
    });
  }

  getCsvErrorsMessages(csvErrors) {
    return csvErrors.map((csvError) => {
      const func = ERRORS[csvError.validationCheckResult.message];
      if (!func) {
        throw new Error(
          "Il manque la chaine de traduction pour l'erreur : " +
            csvError.validationCheckResult.message
        );
      }
      const params = {
        lineNumber: csvError.lineNumber,
        ...csvError.validationCheckResult.messageParams,
      };
      return func(params);
    });
  }
}
