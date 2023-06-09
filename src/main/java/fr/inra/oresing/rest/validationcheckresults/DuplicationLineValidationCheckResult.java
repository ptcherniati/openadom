package fr.inra.oresing.rest.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;
import java.util.SortedSet;

public class DuplicationLineValidationCheckResult implements ValidationCheckResult {

    public static String MESSAGE_FOR_REFERENCES ="duplicatedLineInReference";
    public static String MESSAGE_FOR_DATATYPES ="duplicatedLineInDatatype";
    ValidationLevel level;
    String message;

    Map<String, Object> messageParams;

    public DuplicationLineValidationCheckResult(FileType filetype, String file, ValidationLevel level, Ltree hierarchicalKey, int currentLineNumber, SortedSet<Integer> otherLines) {
        this.level = level;
        if(FileType.REFERENCES.message != null){
            this.message = FileType.REFERENCES.message;
        } else {
            this.message = FileType.DATATYPE.message;
        }
        this.messageParams = ImmutableMap.of(
                    "file", file,
                "lineNumber", currentLineNumber,
                "otherLines", otherLines,
                "duplicateKey", hierarchicalKey.getSql()
        );
    }

    @Override
    public ValidationLevel getLevel() {
        return level;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Map<String, Object> getMessageParams() {
        return this.messageParams;
    }
    public enum FileType{
        DATATYPE(MESSAGE_FOR_DATATYPES),REFERENCES(MESSAGE_FOR_REFERENCES);
        String message;
        FileType(String message) {
            this.message = message;
        }
    };
}