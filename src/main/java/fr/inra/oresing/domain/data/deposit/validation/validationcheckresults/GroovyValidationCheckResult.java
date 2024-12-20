package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;

import java.util.Map;

public record GroovyValidationCheckResult(
        ValidationLevel level,
        String message,
        Map<String, Object> messageParams,
        CheckerTarget target,
        StringType value
) implements CheckerValidationCheckResult<StringType> {

    public static GroovyValidationCheckResult success(final CheckerTarget target, final FieldType value) {
        return new GroovyValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, (StringType) value.copy());
    }

    public static GroovyValidationCheckResult error(final CheckerTarget target, final String message, final ImmutableMap<String, Object> messageParams) {
        return new GroovyValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target, null);
    }
}