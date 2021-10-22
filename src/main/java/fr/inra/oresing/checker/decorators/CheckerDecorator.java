package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.List;
import java.util.Map;

public class CheckerDecorator {
    public static List<ICheckerDecorator> checkerDecorators = List.of(new CodifyDecorator(), new RequiredDecorator());

    public static ValidationCheckResult check(String value, Map<String, String> params, CheckerTarget target) throws DecoratorException {
        if(params == null || params.isEmpty()){
            return DefaultValidationCheckResult.warn(value, null);
        }
        for (ICheckerDecorator checkerDecorator : checkerDecorators) {
            ValidationCheckResult check = checkerDecorator.check(value, params, target);
            if(ValidationLevel.WARN.equals(check.getLevel())){
                value = check.getMessage();
            }else{
                return check;
            }
        }
        return DefaultValidationCheckResult.warn(value, null);
    }
}