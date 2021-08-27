import { IntervalValues } from "./IntervalValues";
import { VariableComponentKey } from "./VariableComponentKey";
export class VariableComponentFilters {
    variableComponentKey;
    filter;
    type;
    format;
    intervalValues;
    isRegExp = false;
    constructor(
        variableComponentFiltersOrVariableComponentKey,
        filter,
        type,
        format,
        intervalValues,
        isRegExp,
    ) {
        if (typeof variableComponentFiltersOrVariableComponentKey == "object") {
            Object.keys(this).forEach(
                (key) =>
                (this[key] = variableComponentFiltersOrVariableComponentKey[key] ?
                    variableComponentFiltersOrVariableComponentKey[key] :
                    null)
            );
        } else {
            this.variableComponentKey = new VariableComponentKey(
                variableComponentFiltersOrVariableComponentKey
            );
            this.filter = filter;
            this.type = type;
            this.format = format;
            this.intervalValues = new IntervalValues(intervalValues);
            this.isRegExp = isRegExp == null ? false : isRegExp;
        }
    }
}