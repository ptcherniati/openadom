package fr.inra.oresing.rest.exceptions.additionalfiles;

import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.rest.AdditionalFileParamsParsingResult;

public class BadAdditionalFileParamsSearchException extends OreSiTechnicalException {

    private final AdditionalFileParamsParsingResult fileParamsParsingResult;

    private BadAdditionalFileParamsSearchException(String message, AdditionalFileParamsParsingResult fileParamsParsingResult) {
        super(message);
        this.fileParamsParsingResult = fileParamsParsingResult;
    }

    public static void check(AdditionalFileParamsParsingResult fileParamsParsingResult) throws BadAdditionalFileParamsSearchException {
        if (!fileParamsParsingResult.isValid()) {
            throw new BadAdditionalFileParamsSearchException("invalid parameters", fileParamsParsingResult);
        }
    }

    public AdditionalFileParamsParsingResult getFileParamsParsingResult() {
        return fileParamsParsingResult;
    }
}