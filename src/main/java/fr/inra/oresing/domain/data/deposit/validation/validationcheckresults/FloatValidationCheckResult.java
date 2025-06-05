package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FloatType;

import java.util.Map;


public record FloatValidationCheckResult(ValidationLevel level,
                                         String message, Map<String, Object> messageParams,
                                         CheckerTarget target,
                                         FloatType value) implements CheckerValidationCheckResult {
    public static FloatValidationCheckResult success(final CheckerTarget target, final FloatType value) {

        return new FloatValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, (FloatType) value.copy());
    }

    public static FloatValidationCheckResult error(final CheckerTarget target, final String message, final ImmutableMap<String, Object> messageParams) {
        return new FloatValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target, null);
    }
}