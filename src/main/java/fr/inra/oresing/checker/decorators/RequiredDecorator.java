package fr.inra.oresing.checker.decorators;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.assertj.core.util.Strings;

import java.util.Map;
import java.util.Optional;

public class RequiredDecorator implements ICheckerDecorator {
    public static final String PARAMS_REQUIRED = "required";

    public ValidationCheckResult check(String value, Map<String, String> params, CheckerTarget target) throws DecoratorException {
        boolean required = params.containsKey(PARAMS_REQUIRED) &&
                Optional.ofNullable(params.get(PARAMS_REQUIRED))
                        .map(req->req==null || Boolean.parseBoolean(req))
                        .orElse(false);
        if (required && Strings.isNullOrEmpty(value)) {
            throw new DecoratorException(DefaultValidationCheckResult.error(target.getInternationalizedKey("requiredValue"), ImmutableMap.of("target", target.getTarget())));
        }else if (!required && Strings.isNullOrEmpty(value)) {
           return DefaultValidationCheckResult.success();
        }
        return DefaultValidationCheckResult.warn(value, null);
    }
}