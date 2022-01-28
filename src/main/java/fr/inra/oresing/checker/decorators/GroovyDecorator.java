package fr.inra.oresing.checker.decorators;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.checker.GroovyLineChecker;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public class GroovyDecorator implements ICheckerDecorator {

    public ValidationCheckResult check(Map<? extends Object, String> values, String value, DecoratorConfiguration params, CheckerTarget target) throws DecoratorException {
        String groovy = params.getGroovy();
        String valueAfterDecoration;
        if (groovy == null) {
            valueAfterDecoration = value;
        } else {
            ImmutableMap<String, Object> context = GroovyLineChecker.buildContext(values, target.getApplication(), params, target.getRepository());
            StringGroovyExpression groovyExpression = StringGroovyExpression.forExpression(groovy);
            valueAfterDecoration = groovyExpression.evaluate(context);
        }
        return DefaultValidationCheckResult.warn(valueAfterDecoration, null);
    }
}