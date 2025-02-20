package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.PatternType;
import fr.inra.oresing.domain.data.deposit.context.column.Column;

import java.util.Map;


public record PatternValidationCheckResult(
        ValidationLevel level,
        String message,
        Map<String, Object> messageParams,
        CheckerTarget target,
        PatternType value
) implements CheckerValidationCheckResult<PatternType> {

    public  static CheckerValidationCheckResult of(CheckerValidationCheckResult checkerValidationCheckResult, PatternType patternType){
        if(checkerValidationCheckResult.isError()){
            return checkerValidationCheckResult;
        }
        patternType.getValue().put(Column.__VALUE__, checkerValidationCheckResult.value());
        return new PatternValidationCheckResult(
                checkerValidationCheckResult.level(),
                checkerValidationCheckResult.message(),
                checkerValidationCheckResult.messageParams(),
                checkerValidationCheckResult.target(),
                patternType
        );
    }
}