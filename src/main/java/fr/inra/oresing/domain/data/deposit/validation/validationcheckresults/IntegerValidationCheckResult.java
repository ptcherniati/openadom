package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.IntegerType;

import java.util.Map;


public record IntegerValidationCheckResult(ValidationLevel level, String message, Map<String, Object> messageParams,
                                           CheckerTarget target,
                                           IntegerType value) implements CheckerValidationCheckResult<IntegerType> {
    public static IntegerValidationCheckResult success(final CheckerTarget target, final IntegerType value) {

        return new IntegerValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, value.copy());
    }

    public static IntegerValidationCheckResult error(final CheckerTarget target, final String message, final ImmutableMap<String, Object> messageParams) {
        return new IntegerValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target, null);
    }
}