export class VariableComponentKey {
  variable;
  component;
  constructor(variableOrVariableComponentKey, component) {
    if (typeof variableOrVariableComponentKey == "object") {
      Object.keys(this).forEach(
        (key) =>
          (this[key] = variableOrVariableComponentKey[key]
            ? variableOrVariableComponentKey[key]
            : null)
      );
    } else {
      this.variable = variableOrVariableComponentKey;
      this.component = component;
    }
  }
}
