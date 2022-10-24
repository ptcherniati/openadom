package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;
import org.assertj.core.util.Strings;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public interface CheckerOnOneVariableComponentLineChecker<C extends LineCheckerConfiguration> extends LineChecker<C> {

    CheckerTarget getTarget();

    LineTransformer getTransformer();

    default ValidationCheckResult check(Datum datum) {
        Datum transformedDatum = getTransformer().transform(datum);
        return checkWithoutTransformation(transformedDatum);
    }

    default ValidationCheckResult checkWithoutTransformation(Datum datum) {
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget();
        String value = datum.get(variableComponentKey);
        ValidationCheckResult validationCheckResult;
        if (Strings.isNullOrEmpty(value)) {
            if (getConfiguration().isRequired()) {
                CheckerTarget target = getTarget();
                validationCheckResult = DefaultValidationCheckResult.error(target.getInternationalizedKey("requiredValue"), ImmutableMap.of("target", target));
            } else {
                validationCheckResult = DefaultValidationCheckResult.success();
            }
        } else {
            validationCheckResult = check(value);
        }
        return validationCheckResult;
    }

    @Override
    default Set<ValidationCheckResult> checkReference(ReferenceDatum referenceDatum) {
        ReferenceDatum transformedReferenceDatum = getTransformer().transform(referenceDatum);
        final ReferenceColumn column = (ReferenceColumn) getTarget();
        final Collection<String> valuesToCheck = transformedReferenceDatum.getValuesToCheck(column);
        final Set<ValidationCheckResult> validationCheckResults = valuesToCheck.stream()
                .map(this::checkRequiredThenCheck)
                .collect(Collectors.toUnmodifiableSet());
        return validationCheckResults;
    }

    default ValidationCheckResult checkRequiredThenCheck(String value) {
        ValidationCheckResult validationCheckResult;
        if (Strings.isNullOrEmpty(value)) {
            if (getConfiguration().isRequired()) {
                CheckerTarget target = getTarget();
                validationCheckResult = DefaultValidationCheckResult.error(target.getInternationalizedKey("requiredValue"), ImmutableMap.of("target", target));
            } else {
                validationCheckResult = DefaultValidationCheckResult.success();
            }
        } else {
            validationCheckResult = check(value);
        }
        return validationCheckResult;
    }

    ValidationCheckResult check(String value);

    SqlPrimitiveType getSqlType();
}