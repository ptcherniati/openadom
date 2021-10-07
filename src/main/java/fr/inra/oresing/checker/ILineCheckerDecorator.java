package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface ILineCheckerDecorator extends CheckerOnOneVariableComponentLineChecker{
    @Override
    default CheckerTarget getTarget() {
        return getChecker().getTarget();
    }

    @Override
    default ValidationCheckResult check(Map<VariableComponentKey, String> values) {
        return CheckerOnOneVariableComponentLineChecker.super.check(values);
    }

    CheckerOnOneVariableComponentLineChecker getChecker();

    @Override
    default boolean instanceOf(Class clazz) {
        if(clazz.isInstance(ILineCheckerDecorator.class)){
            return true;
        }
        return this.getChecker().instanceOf(clazz);
    }
}