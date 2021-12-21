package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.checker.GroovyLineChecker;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;
import java.util.Optional;

public class GroovyDecorator implements ICheckerDecorator {
    public static final String PARAMS_GROOVY = "groovy";

    public ValidationCheckResult check(Map<? extends Object, String> values, String value, Map<String, String> params, CheckerTarget target) throws DecoratorException {

        GroovyExpression groovyExpression=Optional.ofNullable(params.get(PARAMS_GROOVY))
                        .map(req -> GroovyExpression.forExpression(req))
                        .orElse((GroovyExpression) null);
        if (groovyExpression!=null) {
            value = groovyExpression.evaluate(GroovyLineChecker.buildContext(values, target.getApplication(), params, target.getRepository())).toString();

        }
        return DefaultValidationCheckResult.warn(value, null);
    }
}