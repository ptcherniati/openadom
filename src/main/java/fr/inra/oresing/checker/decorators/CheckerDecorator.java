package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.List;

public class CheckerDecorator {
    public static List<ICheckerDecorator> checkerDecorators = List.of( new CodifyDecorator(),new GroovyDecorator(), new RequiredDecorator());

    public static <T> ValidationCheckResult check(SomethingThatCanProvideEvaluationContext values, String value, DecoratorConfiguration params, CheckerTarget target) throws DecoratorException {
        if (params == null) {
            return DefaultValidationCheckResult.warn(value, null);
        }
        for (ICheckerDecorator checkerDecorator : checkerDecorators) {
            ValidationCheckResult check = checkerDecorator.check(values, value, params, target);
            if(ValidationLevel.WARN.equals(check.getLevel())){
                value = check.getMessage();
            }else{
                return check;
            }
        }
        return DefaultValidationCheckResult.warn(value, null);
    }
}