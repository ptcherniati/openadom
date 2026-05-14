package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.LinkedLines;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 *
 * @param target
 * @param level
 * @param rawValue
 * @param matchedReferenceHierarchicalKey
 * @param matchedReferenceId
 * @param message
 * @param messageParams
 * @param value
 */

public record ReferenceValidationCheckResult(CheckerTarget target, ValidationLevel level, String rawValue,
                                             Set<Ltree> matchedReferenceHierarchicalKey,
                                             @JsonIgnore Set<UUID> matchedReferenceId, String message,
                                             Map<String, Object> messageParams,
                                             ReferenceType value) implements CheckerValidationCheckResult<ReferenceType> {
    @JsonIgnore
    public static ReferenceValidationCheckResult success(final CheckerTarget target, final String rawValue, final Set<Ltree> matchedReferenceHierarchicalKey, final Set<UUID> matchedReferenceId,
                                                         final ReferenceType value) {
        final ReferenceType copy = value.copy();
        copy.uuid = value.getUuid();
        return new ReferenceValidationCheckResult(
                target,
                ValidationLevel.SUCCESS,
                rawValue,
                matchedReferenceHierarchicalKey,
                matchedReferenceId,
                null,
                null,
                copy);
    }

    @JsonIgnore
    public static ReferenceValidationCheckResult error(final CheckerTarget target, final String rawValue, final String message, final ImmutableMap<String, Object> messageParams,
                                                       final ReferenceType value) {
        return new ReferenceValidationCheckResult(target, ValidationLevel.ERROR, rawValue, null, null, message, messageParams, value);
    }

    @Override
    public DataColumnValue<?, ?> transform(final LineChecker<?> lineChecker, final DataColumnValue<?, ?> referenceColumnRawValue, final DataColumn dataColumn, final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        return value().transform(lineChecker, referenceColumnRawValue, dataColumn, refsLinkedTo);
    }
}