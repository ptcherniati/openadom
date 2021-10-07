package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface ICheckerDecorator {
    ValidationCheckResult check(String value, Map<String, String> params, CheckerTarget target) throws DecoratorException;
}