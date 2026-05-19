package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.BooleanType;

import java.util.Map;


public record BooleanValidationCheckResult(ValidationLevel level, String message, Map<String, Object> messageParams,
                                           CheckerTarget target,
                                           BooleanType value) implements CheckerValidationCheckResult<BooleanType> {

    public static BooleanValidationCheckResult success(final CheckerTarget target, final BooleanType value) {
        return new BooleanValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, (BooleanType) value.copy());
    }
}