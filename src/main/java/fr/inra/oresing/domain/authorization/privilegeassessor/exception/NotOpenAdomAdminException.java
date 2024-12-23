package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class NotOpenAdomAdminException extends OreSiTechnicalException {
    public static final String openAdomAdmin_REQUIRED_FOR_OPERATION = "openAdomAdmin_REQUIRED_FOR_OPERATION";
    public NotOpenAdomAdminException() {
        super(openAdomAdmin_REQUIRED_FOR_OPERATION);
    }

    public NotOpenAdomAdminException(final Throwable cause) {
        super(openAdomAdmin_REQUIRED_FOR_OPERATION, cause);
    }
}