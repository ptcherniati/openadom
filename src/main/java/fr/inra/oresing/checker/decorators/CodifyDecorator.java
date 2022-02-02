package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.OreSiService;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.assertj.core.util.Strings;

public class CodifyDecorator implements ICheckerDecorator {

    public ValidationCheckResult check(SomethingThatCanProvideEvaluationContext values, String value, DecoratorConfiguration params, CheckerTarget target) throws DecoratorException {
        String valueAfterCodification;
        if (params.isCodify() && !Strings.isNullOrEmpty(value)) {
            valueAfterCodification = OreSiService.escapeKeyComponent(value);
        } else {
            valueAfterCodification = value;
        }
        return DefaultValidationCheckResult.warn(valueAfterCodification, null);
    }
}