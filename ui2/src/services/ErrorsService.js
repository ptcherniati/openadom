import { i18n } from "@/main";

export class ErrorsService {
  static INSTANCE = new ErrorsService();

  getErrorsMessages(errors) {
    const errorsMap = this.getErrorsMap();
    return errors.map((error) => {
      const func = errorsMap.get(error.message);
      return func(error.messageParams);
    });
  }

  getErrorsMap() {
    const errorsMap = new Map();
    errorsMap.set("emptyFile", this.getEmptyFileError);
    errorsMap.set(
      "missingReferenceForChecker",
      this.getMissingReferenceForCheckerError
    );
    errorsMap.set("unsupportedVersion", this.getUnsupportedVersionError);
    errorsMap.set(
      "undeclaredDataGroupForVariable",
      this.getUndeclaredDataGroupForVariableError
    );
    errorsMap.set(
      "variableInMultipleDataGroup",
      this.getVariableInMultipleDataGroupError
    );
    errorsMap.set(
      "unknownVariablesInDataGroup",
      this.getUnknownVariablesInDataGroupError
    );
    errorsMap.set(
      "missingTimeScopeVariableComponentKey",
      this.getMissingTimeScopeVariableComponentKeyError
    );
    errorsMap.set(
      "timeScopeVariableComponentKeyMissingVariable",
      this.getTimeScopeVariableComponentKeyMissingVariableError
    );
    errorsMap.set(
      "timeScopeVariableComponentKeyUnknownVariable",
      this.getTimeScopeVariableComponentKeyUnknownVariableError
    );
    errorsMap.set(
      "timeVariableComponentKeyMissingComponent",
      this.getTimeVariableComponentKeyMissingComponentError
    );
    errorsMap.set(
      "timeVariableComponentKeyUnknownComponent",
      this.getTimeVariableComponentKeyUnknownComponentError
    );
    errorsMap.set(
      "timeScopeVariableComponentWrongChecker",
      this.getTimeScopeVariableComponentWrongCheckerError
    );
    errorsMap.set(
      "timeScopeVariableComponentPatternUnknown",
      this.getTimeScopeVariableComponentPatternUnknownError
    );
    errorsMap.set("unrecognizedProperty", this.getUnrecognizedPropertyError);
    errorsMap.set("invalidFormat", this.getInvalidFormatError);
    errorsMap.set(
      "missingRequiredExpression",
      this.getMissingRequiredExpressionError
    );
    errorsMap.set(
      "illegalGroovyExpression",
      this.getIllegalGroovyExpressionError
    );
    errorsMap.set("unknownCheckerName", this.getUnknownCheckerNameError);
    errorsMap.set("csvBoundToUnknownVariable", this.getCsvBoundToUnknownVariableError);
    errorsMap.set("csvBoundToUnknownVariableComponent", this.getCsvBoundToUnknownVariableComponentError);
    return errorsMap;
  }

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

  getCsvBoundToUnknownVariableError(params) {
    return i18n.t("errors.csvBoundToUnknownVariable", params);
  }

  getCsvBoundToUnknownVariableComponentError(params) {
    return i18n.t("errors.csvBoundToUnknownVariableComponent", params);
  }
}
