package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public sealed interface CheckerValidationCheckResult<T extends FieldType<?>> extends ValidationCheckResult
        permits BooleanValidationCheckResult, DateValidationCheckResult, DefaultCheckerValidationCheckResult, DefaultManyValidationCheckResult, FloatValidationCheckResult, GroovyValidationCheckResult, IntegerValidationCheckResult, PatternValidationCheckResult, ReferenceValidationCheckResult, StringValidationCheckResult {
    T value();

    default DataColumnValue transform(final LineChecker lineChecker, final DataColumnValue referenceColumnRawValue, final DataColumn dataColumn, final Map<String, Map<String, Set<UUID>>> refsLinkedTo) {
        return new DataColumnSingleValue(value().copy());
    }

}