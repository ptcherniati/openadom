package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class FloatChecker implements CheckerOnOneVariableComponentLineChecker<FloatCheckerConfiguration> {
    private final CheckerTarget target;
    private final FloatCheckerConfiguration configuration;

    @JsonIgnore
    private final LineTransformer transformer;

    public CheckerTarget getTarget(){
        return this.target;
    }

    public FloatChecker(CheckerTarget target, FloatCheckerConfiguration configuration, LineTransformer transformer) {
        this.target = target;
        this.configuration = configuration;
        this.transformer = transformer;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        try {
            if(Multiplicity.MANY.equals(getConfiguration().getMultiplicity())){
                final Set<String> values = Arrays.stream(value.split(","))
                        .map(Float::parseFloat)
                        .map(Number::toString)
                        .collect(Collectors.toSet());
                validationCheckResult = DefaultValidationCheckResult.success();
            } else {
                Float.parseFloat(value.replaceAll(",", "."));
                validationCheckResult = DefaultValidationCheckResult.success();
            }
        } catch (NumberFormatException e) {
            validationCheckResult = DefaultValidationCheckResult.error(
                    getTarget().getInternationalizedKey("invalidFloat"), ImmutableMap.of(
                            "target", target,
                            "value", value));
        }
        return validationCheckResult;
    }

    @Override
    public FloatCheckerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public LineTransformer getTransformer() {
        return transformer;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.NUMERIC;
    }
}