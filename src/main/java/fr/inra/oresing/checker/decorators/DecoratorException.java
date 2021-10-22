package fr.inra.oresing.checker.decorators;

import fr.inra.oresing.rest.ValidationCheckResult;

public class DecoratorException extends Throwable {
    ValidationCheckResult validationCheckResult;

    public DecoratorException(ValidationCheckResult validationCheckResult) {
        this.validationCheckResult = validationCheckResult;
    }

    public ValidationCheckResult getValidationCheckResult() {
        return validationCheckResult;
    }
}