package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;

import java.util.Map;

public sealed interface CheckerValidationCheckResult<T extends FieldType<?>> extends ValidationCheckResult
        permits BooleanValidationCheckResult, DateValidationCheckResult, DefaultCheckerValidationCheckResult, DefaultManyValidationCheckResult, FloatValidationCheckResult, GroovyValidationCheckResult, IntegerValidationCheckResult, PatternValidationCheckResult, ReferenceValidationCheckResult, StringValidationCheckResult {
    T value();

    @SuppressWarnings("java:S1452")
    default DataColumnValue<?, ?> transform(final LineChecker<?> lineChecker, final DataColumnValue<?, ?> referenceColumnRawValue, final DataColumn dataColumn, final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        final T v = value();
        if (v == null) {
            return referenceColumnRawValue;
        }
        return new DataColumnSingleValue(v.copy());
    }

}