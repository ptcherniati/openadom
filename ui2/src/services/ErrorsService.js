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
  unexpectedHeaderColumn : (params) => i18n.t("errors.unexpectedHeaderColumn", params),
  headerColumnPatternNotMatching :(params) => i18n.t("errors.headerColumnPatternNotMatching", params),
  unexpectedTokenCount : (params) => i18n.t("errors.unexpectedTokenCount", params),
  invalidHeaders : (params) => i18n.t("errors.invalidHeaders", params),
  duplicatedHeaders : (params) => i18n.t("errors.duplicatedHeaders", params),
  patternNotMatched : (params) => i18n.t("errors.patternNotMatched", params),
  invalidDate : (params) => i18n.t("errors.invalidDate", params),
  invalidInteger : (params) => i18n.t("errors.invalidInteger", params),
  invalidFloat : (params) => i18n.t("errors.invalidFloat", params),
  checkerExpressionReturnedFalse : (params) => i18n.t("errors.checkerExpressionReturnedFalse", params),
  invalidReference: (params) => i18n.t("errors.invalidReference", params)
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
      const messageParams = csvError.validationCheckResult.messageParams;

      Object.entries(messageParams).forEach(([key, value]) => {
        messageParams[key] = JSON.stringify(value);
      });

      const params = {
        lineNumber: csvError.lineNumber,
        ...messageParams,
      };
      return func(params);
    });
  }
}
