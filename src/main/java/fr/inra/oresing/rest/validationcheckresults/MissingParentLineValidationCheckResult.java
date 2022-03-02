package fr.inra.oresing.rest.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MissingParentLineValidationCheckResult implements ValidationCheckResult {
    public static String MISSING_PARENT_LINE_IN_RECURSIVE_REFERENCE ="missingParentLineInRecursiveReference";
    Map<String, Object> messageParams;

    public MissingParentLineValidationCheckResult(long lineNumber, String refType, Ltree missingReferencesKey, Set<Ltree> knownReferences) {
        super();
        this.messageParams = ImmutableMap.of(
                "lineNumber", lineNumber,
                "references", refType,
                "missingReferencesKey", missingReferencesKey.getSql(),
                "knownReferences", knownReferences.stream().map(Ltree::getSql).collect(Collectors.toUnmodifiableSet())
        );
    }

    @Override
    public ValidationLevel getLevel() {
        return ValidationLevel.ERROR;
    }

    @Override
    public String getMessage() {
        return MISSING_PARENT_LINE_IN_RECURSIVE_REFERENCE;
    }

    @Override
    public Map<String, Object> getMessageParams() {
        return this.messageParams;
    }
}