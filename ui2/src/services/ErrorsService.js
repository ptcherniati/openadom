import { i18n } from "@/main";

export class ErrorsService {
  static INSTANCE = new ErrorsService();

  getEmptyFileError() {
    return i18n.t("errors.emptyFile");
  }

  getMissingReferenceForCheckerError(params) {
    return i18n.t("errors.missingReference", params);
  }

  getUnsupportedVersionError(params) {
    return i18n.t("errors.unsupportedVersion", params);
  }

  getUndeclaredDataGroupForVariableError(params) {
    return i18n.t("errors.undeclaredDataGroup", params);
  }

  getVariableInMultipleDataGroupError(params) {
    return i18n.t("errors.variableInMultipleData", params);
  }

  getUnknownVariablesInDataGroupError(params) {
    return i18n.t("errors.unknownVariablesDataGroup", params);
  }

  getMissingTimeScopeVariableComponentKeyError(params) {
    return i18n.t("errors.missingTimeScope", params);
  }

  getTimeScopeVariableComponentKeyMissingVariableError(params) {
    return i18n.t(
      "errors.timeScopeVariableComponentKeyMissingVariable",
      params
    );
  }

  getTimeScopeVariableComponentKeyUnknownVariableError(params) {
    return i18n.t(
      "errors.timeScopeVariableComponentKeyUnknownVariable",
      params
    );
  }

  getTimeVariableComponentKeyMissingComponentError(params) {
    return i18n.t("errors.timeVariableComponentKeyMissingComponent", params);
  }

  getTimeVariableComponentKeyUnknownComponentError(params) {
    return i18n.t("errors.timeVariableComponentKeyUnknownComponent", params);
  }

  getTimeScopeVariableComponentWrongCheckerError(params) {
    return i18n.t("errors.timeScopeVariableComponentWrongChecker", params);
  }

  getTimeScopeVariableComponentPatternUnknownError(params) {
    return i18n.t("errors.timeScopeVariableComponentPatternUnknown", params);
  }

  getUnrecognizedPropertyError(params) {
    return i18n.t("errors.unrecognizedProperty", params);
  }

  getInvalidFormatError(params) {
    return i18n.t("errors.invalidFormat", params);
  }

  getMissingRequiredExpressionError(params) {
    return i18n.t("errors.missingRequiredExpression", params);
  }

  getIllegalGroovyExpressionError(params) {
    return i18n.t("errors.illegalGroovyExpression", params);
  }

  getUnknownCheckerNameError(params) {
    return i18n.t("errors.unknownCheckerName", params);
  }
}
