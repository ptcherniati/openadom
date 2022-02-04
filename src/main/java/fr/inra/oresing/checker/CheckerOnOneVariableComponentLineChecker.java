package fr.inra.oresing.checker;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.decorators.CheckerDecorator;
import fr.inra.oresing.checker.decorators.DecoratorException;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

public interface CheckerOnOneVariableComponentLineChecker<C extends LineCheckerConfiguration> extends LineChecker<C> {

    CheckerTarget getTarget();

    default ValidationCheckResult check(Datum datum) {
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget().getTarget();
        String value = datum.get(variableComponentKey);
        try {
            ValidationCheckResult check = CheckerDecorator.check(datum, value, getConfiguration(), getTarget());
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
    default ValidationCheckResult checkReference(ReferenceDatum referenceDatum) {
        final ReferenceColumn column = (ReferenceColumn) getTarget().getTarget();
        String value = referenceDatum.get(column);
        try {
            ValidationCheckResult check = CheckerDecorator.check(referenceDatum, value, getConfiguration(), getTarget());
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