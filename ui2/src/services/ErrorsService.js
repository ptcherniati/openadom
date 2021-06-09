import { i18n } from "@/main";
//prettier-ignore

const ERRORS = {
  emptyFile: () => i18n.t("errors.emptyFile"),
  missingReferenceForChecker: (params) => i18n.t("errors.missingReferenceForChecker", params),
  unknownReferenceForChecker: (params) => i18n.t("errors.unknownReferenceForChecker", params),
  unsupportedVersion: (params) => i18n.t("errors.unsupportedVersion", params),
  undeclaredDataGroupForVariable: (params) => i18n.t("errors.undeclaredDataGroupForVariable", params),
  variableInMultipleDataGroup: (params) => i18n.t("errors.variableInMultipleData", params),
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
}
