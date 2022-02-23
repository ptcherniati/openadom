package fr.inra.oresing.transformer;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class GroovyExpressionOnOneLineElementTransformer implements TransformOneLineElementTransformer {

    private final StringGroovyExpression groovyExpression;

    private final ImmutableMap<String, Object> context;

    private final CheckerTarget target;

    public GroovyExpressionOnOneLineElementTransformer(StringGroovyExpression groovyExpression, ImmutableMap<String, Object> context, CheckerTarget target) {
        this.groovyExpression = groovyExpression;
        this.context = context;
        this.target = target;
    }

    @Override
    public CheckerTarget getTarget() {
        return target;
    }

    @Override
    public String transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, String value) {
        ImmutableMap<String, Object> context = ImmutableMap.<String, Object>builder()
                .putAll(this.context)
                .putAll(somethingThatCanProvideEvaluationContext.getEvaluationContext())
                .build();
        String transformed = groovyExpression.evaluate(context);
        return transformed;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("groovyExpression", groovyExpression)
                .append("context", context)
                .append("target", target)
                .toString();
    }
}