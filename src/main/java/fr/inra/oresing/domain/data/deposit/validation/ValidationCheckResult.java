package fr.inra.oresing.domain.data.deposit.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;

import java.util.List;
import java.util.Map;

public interface ValidationCheckResult {

    ValidationLevel level();

    String message();

    Map<String, Object> messageParams();

    @JsonIgnore
    default boolean isSuccess() {
        return level().isSuccess();
    }

    @JsonIgnore
    default boolean isError() {
        return level().isError();
    }

    CheckerTarget target();

    @JsonIgnore
    default List<ValidationCheckResult> getValidations() {
        return List.of(this);
    }


    default ValidationCheckResultRest validationCheckResultToRest(long lineNumber) {
        return new ValidationCheckResultRest(
                getClass().getSimpleName(),
                message(),
                messageParams(),
                lineNumber
        );
    }

    ;
}
