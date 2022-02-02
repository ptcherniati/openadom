package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.rest.ValidationCheckResult;

public interface ICheckerDecorator {
    ValidationCheckResult check(SomethingThatCanProvideEvaluationContext values, String value, DecoratorConfiguration params, CheckerTarget target) throws DecoratorException;
}