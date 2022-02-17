package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.groovy.BooleanGroovyExpression;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Optional;

public class GroovyLineChecker implements LineChecker<GroovyLineCheckerConfiguration> {

    public static final String NAME = "GroovyExpression";

    private final BooleanGroovyExpression expression;

    private final ImmutableMap<String, Object> context;

    private final GroovyLineCheckerConfiguration configuration;

    private GroovyLineChecker(BooleanGroovyExpression expression, ImmutableMap<String, Object> context, GroovyLineCheckerConfiguration configuration) {
        this.expression = expression;
        this.context = context;
        this.configuration = configuration;
    }

    public static GroovyLineChecker forExpression(String expression, ImmutableMap<String, Object> context, GroovyLineCheckerConfiguration configuration) {
        return new GroovyLineChecker(BooleanGroovyExpression.forExpression(expression), context, configuration);
    }

    public static Optional<GroovyExpression.CompilationError> validateExpression(String expression) {
        return GroovyExpression.validateExpression(expression);
    }

    @Override
    public GroovyLineCheckerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public ValidationCheckResult check(Datum datum) {
        return doCheck(datum);
    }

    @Override
    public ValidationCheckResult checkReference(ReferenceDatum referenceDatum) {
        return doCheck(referenceDatum);
    }

    private ValidationCheckResult doCheck(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext) {
        ImmutableMap<String, Object> context = ImmutableMap.<String, Object>builder()
                .putAll(this.context)
                .putAll(somethingThatCanProvideEvaluationContext.getEvaluationContext())
                .build();
        Boolean evaluation = expression.evaluate(context);
        if (evaluation) {
            return DefaultValidationCheckResult.success();
        } else {
            return DefaultValidationCheckResult.error(
                    "checkerExpressionReturnedFalse",
                    ImmutableMap.of("expression", expression));
        }
    }
}