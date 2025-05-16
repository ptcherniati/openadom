package fr.inra.oresing.domain.data.deposit.validation.transformer;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Set;

public class GroovyExpressionOnOneLineElementTransformer implements TransformOneLineElementTransformer {

    final Set<String> references;
    private final StringGroovyExpression groovyExpression;
    private final ImmutableMap<String, Object> context;
    private final CheckerTarget target;

    public GroovyExpressionOnOneLineElementTransformer(final StringGroovyExpression groovyExpression,
                                                       final ImmutableMap<String, Object> context,
                                                       final CheckerTarget target,
                                                       final Set<String> references) {
        super();
        this.groovyExpression = groovyExpression;
        this.context = context;
        this.target = target;
        this.references = references;
    }

    @Override
    public CheckerTarget target() {
        return target;
    }

    @Override
    public FieldType<?> transform(final SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, final FieldType<?> value) {
        final ImmutableMap<String, Object> context = ImmutableMap.<String, Object>builder()
                .putAll(this.context)
                .putAll(somethingThatCanProvideEvaluationContext.getEvaluationContext())
                .build();
        return StringType.getStringTypeFromStringValue(groovyExpression.evaluate(context));
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