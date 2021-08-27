export class VariableComponentOrderBy {
    variableComponentKey;
    order;
    type;
    format;
    static ORDER() {
        return ["ASC", "DESC"];
    }

    constructor(variableComponentOrderByOrVariableComponentKey, order, type, format) {
        if (typeof variableComponentOrderByOrVariableComponentKey == "object") {
            Object.keys(this).forEach(
                (key) =>
                (this[key] = variableComponentOrderByOrVariableComponentKey[key] ?
                    variableComponentOrderByOrVariableComponentKey[key] :
                    null)
            );
        } else {
            this.variableComponentKey = variableComponentOrderByOrVariableComponentKey;
            this.order = this.ORDER.includes(order) ? order : null;
            this.type = type;
            this.format = format;
        }
    }
}