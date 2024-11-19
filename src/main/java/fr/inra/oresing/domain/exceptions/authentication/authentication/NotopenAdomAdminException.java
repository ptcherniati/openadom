package fr.inra.oresing.domain.exceptions.authentication.authentication;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class NotopenAdomAdminException extends OreSiTechnicalException {
    public final static String openAdomAdmin_REQUIRED_FOR_OPERATION = "openAdomAdmin_REQUIRED_FOR_OPERATION";
    public NotopenAdomAdminException() {
        super(openAdomAdmin_REQUIRED_FOR_OPERATION);
    }

    public NotopenAdomAdminException(final Throwable cause) {
        super(openAdomAdmin_REQUIRED_FOR_OPERATION, cause);
    }
}