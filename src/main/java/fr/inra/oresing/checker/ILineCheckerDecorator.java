package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface ILineCheckerDecorator extends CheckerOnOneVariableComponentLineChecker{
    @Override
    default VariableComponentKey getVariableComponentKey() {
        return getChecker().getVariableComponentKey();
    }

    @Override
    default String getColumn() {
        return getChecker().getColumn();
    }

    @Override
    default ValidationCheckResult check(Map<VariableComponentKey, String> values) {
        return CheckerOnOneVariableComponentLineChecker.super.check(values);
    }

    CheckerOnOneVariableComponentLineChecker getChecker();
}
