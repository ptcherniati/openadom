package fr.inra.oresing.domain.data.deposit.validation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.CheckerTarget;

import java.util.Map;

public class DuplicationLineValidationCheckResult implements ValidationCheckResult {

    public static final String MESSAGE_FOR_REFERENCES ="duplicatedLineInReference";
    public static final String MESSAGE_FOR_DATATYPES ="duplicatedLineInDatatype";
    final ValidationLevel level;
    final String message;

    final Map<String, Object> messageParams;

    @Override
    public CheckerTarget target() {
        return target;
    }

    CheckerTarget target;

    public DuplicationLineValidationCheckResult(final FileType filetype,
                                                final String file,
                                                final ValidationLevel level,
                                                final Ltree hierarchicalKey,
                                                final long currentLineNumber,
                                                final ImmutableSortedSet<Long> otherLines,
                                                final CheckerTarget target) {
        super();
        this.level = level;
        if (FileType.REFERENCES.message != null) {
            message = FileType.REFERENCES.message;
        } else {
            message = FileType.DATATYPE.message;
        }
        messageParams = ImmutableMap.of(
                "file", file,
                "lineNumber", currentLineNumber,
                "otherLines", otherLines,
                "duplicateKey", hierarchicalKey.getSql()
        );
    }

    @Override
    public ValidationLevel level() {
        return level;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Map<String, Object> messageParams() {
        return messageParams;
    }
    public enum FileType{
        DATATYPE(MESSAGE_FOR_DATATYPES),REFERENCES(MESSAGE_FOR_REFERENCES);
        final String message;
        FileType(final String message) {
            this.message = message;
        }
    }

}