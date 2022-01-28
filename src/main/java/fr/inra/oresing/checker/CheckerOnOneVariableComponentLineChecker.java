package fr.inra.oresing.checker;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.decorators.CheckerDecorator;
import fr.inra.oresing.checker.decorators.DecoratorException;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface CheckerOnOneVariableComponentLineChecker<C extends LineCheckerConfiguration> extends LineChecker<C> {

    CheckerTarget getTarget();

    default ValidationCheckResult check(Map<VariableComponentKey, String> values) {
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget().getTarget();
        String value = values.get(variableComponentKey);
        try {
            ValidationCheckResult check = CheckerDecorator.check(values, value, getConfiguration(), getTarget());
            if(ValidationLevel.WARN.equals(check.getLevel())){
                value = check.getMessage();
            }else{
                return check;
            }
        } catch (DecoratorException e) {
            return e.getValidationCheckResult();
        }
        return check(value);
    }

    @Override
    default ValidationCheckResult checkReference(Map<String, String> values) {
        String value = values.get(getTarget().getTarget());
        try {
            ValidationCheckResult check = CheckerDecorator.check(values, value, getConfiguration(), getTarget());
            if(ValidationLevel.WARN.equals(check.getLevel())){
                value = check.getMessage();
            }else{
                return check;
            }
        } catch (DecoratorException e) {
            return e.getValidationCheckResult();
        }
        return check(value);
    }

    ValidationCheckResult check(String value);
}