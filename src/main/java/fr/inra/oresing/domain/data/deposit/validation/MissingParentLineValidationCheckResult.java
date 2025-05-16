package fr.inra.oresing.domain.data.deposit.validation;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.data.DataValue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MissingParentLineValidationCheckResult implements ValidationCheckResult {
    public static final String MISSING_PARENT_LINE_IN_RECURSIVE_REFERENCE = "missingParentLineInRecursiveReference";
    final Map<String, Object> messageParams;
    CheckerTarget target;


    public MissingParentLineValidationCheckResult(final long lineNumber, final String refType, final Ltree missingReferencesKey, final Set<DataValue.LineIdentityColumnName> knownReferences) {
        messageParams = ImmutableMap.of(
                "lineNumber", lineNumber,
                "reference", refType,
                "missingReferencesKey", missingReferencesKey.getSql(),
                "knownReferences", knownReferences.stream().map(DataValue.LineIdentityColumnName::naturalKey).map(Ltree::getSql).collect(Collectors.toUnmodifiableSet())
        );
    }

    @Override
    public CheckerTarget target() {
        return target;
    }

    @Override
    public ValidationLevel level() {
        return ValidationLevel.ERROR;
    }

    @Override
    public String message() {
        return MISSING_PARENT_LINE_IN_RECURSIVE_REFERENCE;
    }

    @Override
    public Map<String, Object> messageParams() {
        return messageParams;
    }
}