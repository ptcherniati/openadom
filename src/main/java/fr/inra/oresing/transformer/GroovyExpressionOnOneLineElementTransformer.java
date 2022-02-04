package fr.inra.oresing.transformer;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.checker.GroovyLineChecker;
import fr.inra.oresing.checker.decorators.DecoratorConfiguration;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;

public class GroovyExpressionOnOneLineElementTransformer implements TransformOneLineElementTransformer {

    private final CheckerTarget target;

    private final DecoratorConfiguration configuration;

    public GroovyExpressionOnOneLineElementTransformer(DecoratorConfiguration configuration, CheckerTarget target) {
        this.target = target;
        this.configuration = configuration;
    }

    @Override
    public CheckerTarget getTarget() {
        return target;
    }

    @Override
    public String transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, String value) {
        ImmutableMap<String, Object> context = GroovyLineChecker.buildContext(somethingThatCanProvideEvaluationContext, target.getApplication(), configuration, target.getRepository());
        StringGroovyExpression groovyExpression = StringGroovyExpression.forExpression(configuration.getGroovy());
        String transformedValue = groovyExpression.evaluate(context);
        return transformedValue;
    }
}