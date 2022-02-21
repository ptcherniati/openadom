package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;
import org.assertj.core.util.Strings;

public interface CheckerOnOneVariableComponentLineChecker<C extends LineCheckerConfiguration> extends LineChecker<C> {

    CheckerTarget getTarget();

    LineTransformer getTransformer();

    default ValidationCheckResult check(Datum datum) {
        Datum transformedDatum = getTransformer().transform(datum);
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget().getTarget();
        String value = transformedDatum.get(variableComponentKey);
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

    @Override
    default ValidationCheckResult checkReference(ReferenceDatum referenceDatum) {
        ReferenceDatum transformedReferenceDatum = getTransformer().transform(referenceDatum);
        final ReferenceColumn column = (ReferenceColumn) getTarget().getTarget();
        String value = transformedReferenceDatum.get(column);
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

    SqlPrimitiveType getSqlType();
}