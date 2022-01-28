package fr.inra.oresing.checker.decorators;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.assertj.core.util.Strings;

import java.util.Map;

public class RequiredDecorator implements ICheckerDecorator {

    public ValidationCheckResult check(Map<? extends Object, String> values, String value, DecoratorConfiguration params, CheckerTarget target) throws DecoratorException {
        boolean required = params.isRequired();
        if (required && Strings.isNullOrEmpty(value)) {
            throw new DecoratorException(DefaultValidationCheckResult.error(target.getInternationalizedKey("requiredValue"), ImmutableMap.of("target", target.getTarget())));
        }else if (!required && Strings.isNullOrEmpty(value)) {
           return DefaultValidationCheckResult.success();
        }
        return DefaultValidationCheckResult.warn(value, null);
    }
}