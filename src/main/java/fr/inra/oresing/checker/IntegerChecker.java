package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;

public class IntegerChecker implements CheckerOnOneVariableComponentLineChecker<IntegerCheckerConfiguration> {
    private final CheckerTarget target;
    private final IntegerCheckerConfiguration configuration;
    @JsonIgnore
    private final LineTransformer transformer;

    public CheckerTarget getTarget(){
        return this.target;
    }

    public IntegerChecker(CheckerTarget target, IntegerCheckerConfiguration configuration, LineTransformer transformer) {
        this.configuration = configuration;
        this.target = target;
        this.transformer = transformer;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        try {
            Integer.parseInt(value);
            validationCheckResult = DefaultValidationCheckResult.success();
        } catch (NumberFormatException e) {
            validationCheckResult = DefaultValidationCheckResult.error(
                    getTarget().getInternationalizedKey("invalidInteger"),
                    ImmutableMap.of(
                            "target", target,
                            "value", value
                    )
            );
        }
        return validationCheckResult;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.INTEGER;
    }

    @Override
    public IntegerCheckerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public LineTransformer getTransformer() {
        return transformer;
    }
}