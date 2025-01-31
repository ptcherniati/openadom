package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class NotOpenAdomAdminException extends OreSiTechnicalException {
    public static final String OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION = "OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION";
    public NotOpenAdomAdminException() {
        super(OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION);
    }

    public NotOpenAdomAdminException(final Throwable cause) {
        super(OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION, cause);
    }
}