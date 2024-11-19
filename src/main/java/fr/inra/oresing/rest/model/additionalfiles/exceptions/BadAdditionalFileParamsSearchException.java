package fr.inra.oresing.rest.model.additionalfiles.exceptions;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.model.additionalfiles.exception.AdditionalFileParamsParsingResult;
import lombok.Getter;

@Getter
public class BadAdditionalFileParamsSearchException extends OreSiTechnicalException {

    private final AdditionalFileParamsParsingResult fileParamsParsingResult;

    private BadAdditionalFileParamsSearchException(final String message, final AdditionalFileParamsParsingResult fileParamsParsingResult) {
        super(message);
        this.fileParamsParsingResult = fileParamsParsingResult;
    }

    public static void check(final AdditionalFileParamsParsingResult fileParamsParsingResult) throws BadAdditionalFileParamsSearchException {
        if (!fileParamsParsingResult.isValid()) {
            throw new BadAdditionalFileParamsSearchException("invalid parameters", fileParamsParsingResult);
        }
    }

}