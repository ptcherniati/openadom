package fr.inra.oresing.rest.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;
import java.util.Set;

public class MissingParentLineValidationCheckResult implements ValidationCheckResult {
    public static String MISSING_PARENT_LINE_IN_RECURSIVE_REFERENCE ="missingParentLineInRecursiveReference";
    Map<String, Object> messageParams;

    public MissingParentLineValidationCheckResult(long lineNumber, String refType, String missingReferencesKey, Set<String> knownReferences ) {
        super();
        this.messageParams = ImmutableMap.of(
                "lineNumber", lineNumber,
                "references", refType,
                "missingReferencesKey", missingReferencesKey,
                "knownReferences", knownReferences
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