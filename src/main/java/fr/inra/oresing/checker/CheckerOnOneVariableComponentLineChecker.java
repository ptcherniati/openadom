package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.assertj.core.util.Strings;

public interface CheckerOnOneVariableComponentLineChecker<C extends LineCheckerConfiguration> extends LineChecker<C> {

    CheckerTarget getTarget();

    default ValidationCheckResult check(Datum datum) {
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget().getTarget();
        String value = datum.get(variableComponentKey);
        return checkRequiredAndCheck(value);
    }

    @Override
    default ValidationCheckResult checkReference(ReferenceDatum referenceDatum) {
        final ReferenceColumn column = (ReferenceColumn) getTarget().getTarget();
        String value = referenceDatum.get(column);
        return checkRequiredAndCheck(value);
    }

    private ValidationCheckResult checkRequiredAndCheck(String value) {
        ValidationCheckResult validationCheckResult;
        if (Strings.isNullOrEmpty(value)) {
            if (getConfiguration().isRequired()) {
                CheckerTarget target = getTarget();
                validationCheckResult = DefaultValidationCheckResult.error(target.getInternationalizedKey("requiredValue"), ImmutableMap.of("target", target.getTarget()));
            } else {
                validationCheckResult = DefaultValidationCheckResult.success();
            }
        } else {
            validationCheckResult = check(value);
        }
        return validationCheckResult;
    }

    ValidationCheckResult check(String value);
}