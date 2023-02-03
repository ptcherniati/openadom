import { i18n } from "@/main";
//prettier-ignore

const ERRORS = {
  authorizationScopeMissingReferenceCheckerForAuthorizationScope: (params) => i18n.t("errors.authorizationScopeMissingReferenceCheckerForAuthorizationScope", params),
  authorizationScopeVariableComponentKeyMissingVariable: (params) => i18n.t("errors.authorizationScopeVariableComponentKeyMissingVariable", params),
  authorizationScopeVariableComponentKeyUnknownVariable: (params) => i18n.t("errors.authorizationScopeVariableComponentKeyUnknownVariable", params),
  authorizationScopeVariableComponentReftypeNull: (params) => i18n.t("errors.authorizationScopeVariableComponentReftypeNull", params),
  authorizationScopeVariableComponentReftypeUnknown: (params) => i18n.t("errors.authorizationScopeVariableComponentReftypeUnknown", params),
  authorizationScopeVariableComponentWrongChecker: (params) => i18n.t("errors.authorizationScopeVariableComponentWrongChecker", params),
  authorizationVariableComponentKeyMissingComponent: (params) => i18n.t("errors.authorizationVariableComponentKeyMissingComponent", params),
  authorizationVariableComponentKeyUnknownComponent: (params) => i18n.t("errors.authorizationVariableComponentKeyUnknownComponent", params),
  badauthorizationscopeforrepository: (params) => i18n.t("errors.badauthorizationscopeforrepository", params),
  checkerExpressionReturnedFalse: (params) => i18n.t("errors.checkerExpressionReturnedFalse", params),
  csvBoundToUnknownVariable: (params) => i18n.t("errors.csvBoundToUnknownVariable", params),
  csvBoundToUnknownVariableComponent: (params) => i18n.t("errors.csvBoundToUnknownVariableComponent", params),
  duplicatedLineInDatatype: (params) => i18n.t("errors.duplicatedLineInDatatype", params),
  duplicatedLineInReference: (params) => i18n.t("errors.duplicatedLineInReference", params),
  duplicatedHeaders: (params) => i18n.t("errors.duplicatedHeaders", params),
  emptyFile: (params) => i18n.t("errors.emptyFile", params),
  emptyHeader: (params) => i18n.t("errors.emptyHeader", params),
  headerColumnPatternNotMatching: (params) => i18n.t("errors.headerColumnPatternNotMatching", params),
  illegalCheckerConfigurationParameterForReferenceColumnChecker: (params) => i18n.t("errors.illegalCheckerConfigurationParameterForReferenceColumnChecker", params),
  illegalCheckerConfigurationParameterForValidationRuleInDataType: (params) => i18n.t("errors.illegalCheckerConfigurationParameterForValidationRuleInDataType", params),
  illegalCheckerConfigurationParameterForValidationRuleInReference: (params) => i18n.t("errors.illegalCheckerConfigurationParameterForValidationRuleInReference", params),
  illegalCheckerConfigurationParameterForVariableComponentChecker: (params) => i18n.t("errors.illegalCheckerConfigurationParameterForVariableComponentChecker", params),
  illegalGroovyExpressionForValidationRuleInDataType: (params) => i18n.t("errors.illegalGroovyExpressionForValidationRuleInDataType", params),
  illegalGroovyExpressionForValidationRuleInReference: (params) => i18n.t("errors.illegalGroovyExpressionForValidationRuleInReference", params),
  invalidDate: (params) => i18n.t("errors.invalidDate", params),
  invalidDateWithColumn: (params) => i18n.t("errors.invalidDateWithColumn", params),
  invalidDurationForDateCheckerForValidationRuleInDataType: (params) => i18n.t("errors.invalidDurationForDateCheckerForValidationRuleInDataType", params),
  invalidDurationForDateCheckerForValidationRuleInReference: (params) => i18n.t("errors.invalidDurationForDateCheckerForValidationRuleInReference", params),
  invalidDurationForReferenceColumnDateChecker: (params) => i18n.t("errors.invalidDurationForReferenceColumnDateChecker", params),
  invalidDurationForVariableComponentDateChecker: (params) => i18n.t("errors.invalidDurationForVariableComponentDateChecker", params),
  invalidFloat: (params) => i18n.t("errors.invalidFloat", params),
  invalidFloatWithColumn: (params) => i18n.t("errors.invalidFloatWithColumn", params),
  invalidFormat: (params) => i18n.t("errors.invalidFormat", params),
  invalidHeaders: (params) => i18n.t("errors.invalidHeaders", params),
  invalidInteger: (params) => i18n.t("errors.invalidInteger", params),
  invalidIntegerWithColumn: (params) => i18n.t("errors.invalidIntegerWithColumn", params),
  invalidInternationalizedColumns: (params) => i18n.t("errors.invalidInternationalizedColumns", params),
  invalidInternationalizedColumnsForDataType: (params) => i18n.t("errors.invalidInternationalizedColumnsForDataType", params),
  invalidKeyColumns: (params) => i18n.t("errors.invalidKeyColumns", params),
  invalidPatternForDateCheckerForValidationRuleInDataType: (params) => i18n.t("errors.invalidPatternForDateCheckerForValidationRuleInDataType", params),
  invalidPatternForDateCheckerForValidationRuleInReference: (params) => i18n.t("errors.invalidPatternForDateCheckerForValidationRuleInReference", params),
  invalidPatternForReferenceColumnDateChecker: (params) => i18n.t("errors.invalidPatternForReferenceColumnDateChecker", params),
  invalidPatternForReferenceColumnRegularExpressionChecker: (params) => i18n.t("errors.invalidPatternForReferenceColumnRegularExpressionChecker", params),
  invalidPatternForRegularExpressionCheckerForValidationRuleInDataType: (params) => i18n.t("errors.invalidPatternForRegularExpressionCheckerForValidationRuleInDataType", params),
  invalidPatternForRegularExpressionCheckerForValidationRuleInReference: (params) => i18n.t("errors.invalidPatternForRegularExpressionCheckerForValidationRuleInReference", params),
  invalidPatternForVariableComponentDateChecker: (params) => i18n.t("errors.invalidPatternForVariableComponentDateChecker", params),
  invalidPatternForVariableComponentRegularExpressionChecker: (params) => i18n.t("errors.invalidPatternForVariableComponentRegularExpressionChecker", params),
  invalidReference: (params) => i18n.t("errors.invalidReference", params),
  missingTagDeclaration: (params) => i18n.t("errors.missingTagDeclaration", params),
  missingReferentielTagDeclaration: (params) => i18n.t("errors.missingReferentielTagDeclaration", params),
  missingReferenceColumnsTagDeclaration: (params) => i18n.t("errors.missingReferenceColumnsTagDeclaration", params),
  missingDataTypeTagDeclaration: (params) => i18n.t("errors.missingDataTypeTagDeclaration", params),
  missingVariableDescriptionTagDeclaration: (params) => i18n.t("errors.missingVariableDescriptionTagDeclaration", params),
  missingVariableComponentDescriptionTagDeclaration: (params) => i18n.t("errors.missingVariableComponentDescriptionTagDeclaration", params),
  invalidReferenceWithColumn: (params) => i18n.t("errors.invalidReferenceWithColumn", params),
  missingAggregationComponentForChart: (params) => i18n.t("errors.missingAggregationComponentForChart", params),
  missingAggregationVariableForChart: (params) => i18n.t("errors.missingAggregationVariableForChart", params),
  missingAuthorizationForDatatype: (params) => i18n.t("errors.missingAuthorizationForDatatype", params),
  missingAuthorizationScopeVariableComponentKey: (params) => i18n.t("errors.missingAuthorizationScopeVariableComponentKey", params),
  missingBoundToForConstantDescription: (params) => i18n.t("errors.missingBoundToForConstantDescription", params),
  missingColumnNumberOrHeaderNameForConstantDescription: (params) => i18n.t("errors.missingColumnNumberOrHeaderNameForConstantDescription", params),
  missingColumnReferenceForCheckerInDataType: (params) => i18n.t("errors.missingColumnReferenceForCheckerInDataType", params),
  missingColumnReferenceForCheckerInReference: (params) => i18n.t("errors.missingColumnReferenceForCheckerInReference", params),
  missingExportHeaderNameForConstantDescription: (params) => i18n.t("errors.missingExportHeaderNameForConstantDescription", params),
  missingKeyColumnsForReference: (params) => i18n.t("errors.missingKeyColumnsForReference", params),
  missingParamColumnReferenceForCheckerInDataType: (params) => i18n.t("errors.missingParamColumnReferenceForCheckerInDataType", params),
  missingParamColumnReferenceForCheckerInReference: (params) => i18n.t("errors.missingParamColumnReferenceForCheckerInReference", params),
  missingParentColumnForReferenceInCompositeReference: (params) => i18n.t("errors.missingParentColumnForReferenceInCompositeReference", params),
  missingParentLineInRecursiveReference: (params) => i18n.t("errors.missingParentLineInRecursiveReference", params),
  missingParentRecursiveKeyColumnForReferenceInCompositeReference: (params) => i18n.t("errors.missingParentRecursiveKeyColumnForReferenceInCompositeReference", params),
  missingReferenceForChecker: (params) => i18n.t("errors.missingReferenceForChecker", params),
  missingReferenceForCheckerInDataType: (params) => i18n.t("errors.missingReferenceForCheckerInDataType", params),
  missingReferenceForCheckerInReference: (params) => i18n.t("errors.missingReferenceForCheckerInReference", params),
  missingReferenceForCheckerInReferenceColumn: (params) => i18n.t("errors.missingReferenceForCheckerInReferenceColumn", params),
  missingReferenceInCompositereference: (params) => i18n.t("errors.missingReferenceInCompositereference", params),
  missingRequiredExpressionForValidationRuleInDataType: (params) => i18n.t("errors.missingRequiredExpressionForValidationRuleInDataType", params),
  missingRequiredExpressionForValidationRuleInReference: (params) => i18n.t("errors.missingRequiredExpressionForValidationRuleInReference", params),
  missingRowLineForConstantDescription: (params) => i18n.t("errors.missingRowLineForConstantDescription", params),
  missingStandardDeviationComponentForChart: (params) => i18n.t("errors.missingStandardDeviationComponentForChart", params),
  missingTimeScopeVariableComponentKey: (params) => i18n.t("errors.missingTimeScopeVariableComponentKey", params),
  missingUnitComponentForChart: (params) => i18n.t("errors.missingUnitComponentForChart", params),
  missingValueComponentForChart: (params) => i18n.t("errors.missingValueComponentForChart", params),
  overlappingpublishedversion: (params) => i18n.t("errors.overlappingpublishedversion", params),
  patternNotMatched: (params) => i18n.t("errors.patternNotMatched", params),
  patternNotMatchedWithColumn: (params) => i18n.t("errors.patternNotMatchedWithColumn", params),
  requiredParentKeyColumnInCompositeReferenceForReference: (params) => i18n.t("errors.requiredParentKeyColumnInCompositeReferenceForReference", params),
  requiredReferenceInCompositeReferenceForParentKeyColumn: (params) => i18n.t("errors.requiredReferenceInCompositeReferenceForParentKeyColumn", params),
  requiredValue: (params) => i18n.t("errors.requiredValue", params),
  requiredValueWithColumn: (params) => i18n.t("errors.requiredValueWithColumn", params),
  sameHeaderLineAndFirstRowLineForConstantDescription: (params) => i18n.t("errors.sameHeaderLineAndFirstRowLineForConstantDescription", params),
  timeScopeVariableComponentKeyMissingVariable: (params) => i18n.t("errors.timeScopeVariableComponentKeyMissingVariable", params),
  timeScopeVariableComponentKeyUnknownVariable: (params) => i18n.t("errors.timeScopeVariableComponentKeyUnknownVariable", params),
  timeScopeVariableComponentPatternUnknown: (params) => i18n.t("errors.timeScopeVariableComponentPatternUnknown", params),
  timeScopeVariableComponentWrongChecker: (params) => i18n.t("errors.timeScopeVariableComponentWrongChecker", params),
  timeVariableComponentKeyMissingComponent: (params) => i18n.t("errors.timeVariableComponentKeyMissingComponent", params),
  timeVariableComponentKeyUnknownComponent: (params) => i18n.t("errors.timeVariableComponentKeyUnknownComponent", params),
  timerangeoutofinterval: (params) => i18n.t("errors.timerangeoutofinterval", params),
  tooBigRowLineForConstantDescription: (params) => i18n.t("errors.tooBigRowLineForConstantDescription", params),
  tooLittleRowLineForConstantDescription: (params) => i18n.t("errors.tooLittleRowLineForConstantDescription", params),
  unDeclaredValueForChart: (params) => i18n.t("errors.unDeclaredValueForChart", params),
  undeclaredDataGroupForVariable: (params) => i18n.t("errors.undeclaredDataGroupForVariable", params),
  unexpectedHeaderColumn: (params) => i18n.t("errors.unexpectedHeaderColumn", params),
  unexpectedTokenCount: (params) => i18n.t("errors.unexpectedTokenCount", params),
  unknownCheckerNameForValidationRuleInDataType: (params) => i18n.t("errors.unknownCheckerNameForValidationRuleInDataType", params),
  unknownCheckerNameForValidationRuleInReference: (params) => i18n.t("errors.unknownCheckerNameForValidationRuleInReference", params),
  unknownCheckerNameForVariableComponent: (params) => i18n.t("errors.unknownCheckerNameForVariableComponent", params),
  unknownCheckerNameForVariableComponentCheckerInDataType: (params) => i18n.t("errors.unknownCheckerNameForVariableComponentCheckerInDataType", params),
  unknownCheckerNameForVariableComponentCheckerInReference: (params) => i18n.t("errors.unknownCheckerNameForVariableComponentCheckerInReference", params),
  unknownCheckerNameInReferenceColumn: (params) => i18n.t("errors.unknownCheckerNameInReferenceColumn", params),
  unknownIllegalException: (params) => i18n.t("errors.unknownIllegalException", params),
  unknownReferenceForChecker: (params) => i18n.t("errors.unknownReferenceForChecker", params),
  unknownReferenceForCheckerInDataType: (params) => i18n.t("errors.unknownReferenceForCheckerInDataType", params),
  unknownReferenceForCheckerInReference: (params) => i18n.t("errors.unknownReferenceForCheckerInReference", params),
  unknownReferenceForCheckerInReferenceColumn: (params) => i18n.t("errors.unknownReferenceForCheckerInReferenceColumn", params),
  unknownReferenceInCompositeReference: (params) => i18n.t("errors.unknownReferenceInCompositeReference", params),
  unknownReferenceInDatatypeReferenceDisplay: (params) => i18n.t("errors.unknownReferenceInDatatypeReferenceDisplay", params),
  unknownUsedAsVariableComponentUniqueness: (params) => i18n.t("errors.unknownUsedAsVariableComponentUniqueness", params),
  unknownVariablesInDataGroup: (params) => i18n.t("errors.unknownVariablesInDataGroup", params),
  unrecognizedProperty: (params) => i18n.t("errors.unrecognizedProperty", params),
  unsupportedVersion: (params) => i18n.t("errors.unsupportedVersion", params),
  noCapturingGroupForDatatypeRepository: (params) => i18n.t("errors.noCapturingGroupForDatatypeRepository", params),
  invalidPatternForDatatypeRepository: (params) => i18n.t("errors.invalidPatternForDatatypeRepository", params),
  invalidCapturingGroupForDatatypeRepositoryAuthorizationScope: (params) => i18n.t("errors.invalidCapturingGroupForDatatypeRepositoryAuthorizationScope", params),
  invalidCapturingGroupForDatatypeRepository: (params) => i18n.t("errors.invalidCapturingGroupForDatatypeRepository", params),
  invalidCapturingGroupForStartDateDatatypeRepositoryDate: (params) => i18n.t("errors.invalidCapturingGroupForStartDateDatatypeRepositoryDate", params),
  invalidCapturingGroupForEndDateDatatypeRepositoryDate: (params) => i18n.t("errors.invalidCapturingGroupForEndDateDatatypeRepositoryDate", params),
  variableInMultipleDataGroup: (params) => i18n.t("errors.variableInMultipleDataGroup", params),
};

export class ErrorsService {
  static INSTANCE = new ErrorsService();

  getErrorsMessages(errors) {
    return errors.map((error) => {
      const func = ERRORS[error.message];
      if (!func) {
        //throw new Error("Il manque la chaine de traduction pour l'erreur : " + error.message);
        return i18n.t("errors.exception");
      }
      return func(error.messageParams);
    });
  }

  getCsvErrorsMessages(csvErrors) {
    return csvErrors.map((csvError) => {
      const func = ERRORS[csvError.validationCheckResult.message];
      // console.log(csvError.validationCheckResult.messageParams.target);
      if (csvError.validationCheckResult.messageParams.target != null) {
        if (csvError.validationCheckResult.messageParams.target.column != null) {
          csvError.validationCheckResult.messageParams.target =
            csvError.validationCheckResult.messageParams.target.column;
        }
        if (csvError.validationCheckResult.messageParams.target.id != null) {
          csvError.validationCheckResult.messageParams.target =
            csvError.validationCheckResult.messageParams.target.id;
        }
      }
      if (
        csvError.validationCheckResult.messageParams.expectedValue != null ||
        csvError.validationCheckResult.messageParams.givenValue != null
      ) {
        if (csvError.validationCheckResult.messageParams.expectedValue.sql != null) {
          csvError.validationCheckResult.messageParams.expectedValue =
            csvError.validationCheckResult.messageParams.expectedValue.sql;
        }
        if (csvError.validationCheckResult.messageParams.givenValue.sql != null) {
          csvError.validationCheckResult.messageParams.givenValue =
            csvError.validationCheckResult.messageParams.givenValue.sql;
        }
      }
      if (!func) {
        //throw new Error("Il manque la chaine de traduction pour l'erreur : " + csvError.validationCheckResult.message);
        return Error(i18n.t("errors.exception") + csvError.validationCheckResult.message);
      }
      const params = {
        lineNumber: csvError.lineNumber,
        ...csvError.validationCheckResult.messageParams,
      };
      return func(params);
    });
  }
}