package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.OreSiService;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.assertj.core.util.Strings;

import java.util.Map;
import java.util.Optional;

public class CodifyDecorator implements ICheckerDecorator {
    public static final String PARAMS_CODIFY = "codify";

    public ValidationCheckResult check(String value, Map<String, String> params, CheckerTarget target) throws DecoratorException {

        boolean codify = params.containsKey(PARAMS_CODIFY) &&
                Optional.ofNullable(params.get(PARAMS_CODIFY))
                        .map(req -> req == null || Boolean.parseBoolean(req))
                        .orElse(false);
        if (codify && !Strings.isNullOrEmpty(value)) {
            value = OreSiService.escapeKeyComponent(value);
        }
        return DefaultValidationCheckResult.warn(value, null);
    }
}