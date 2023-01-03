package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiTechnicalException;

public class NotSuperAdminException extends OreSiTechnicalException {
    public final static String SUPER_ADMIN_REQUIRED_FOR_OPERATION = "SUPER_ADMIN_REQUIRED_FOR_OPERATION";
    public NotSuperAdminException() {
        super(SUPER_ADMIN_REQUIRED_FOR_OPERATION);
    }

    public NotSuperAdminException(Throwable cause) {
        super(SUPER_ADMIN_REQUIRED_FOR_OPERATION, cause);
    }
}