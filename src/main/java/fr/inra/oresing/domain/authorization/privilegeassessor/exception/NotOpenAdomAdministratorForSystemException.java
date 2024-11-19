package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class NotOpenAdomAdministratorForSystemException extends OreSiTechnicalException {
    public final static String NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM = "NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM";
    public NotOpenAdomAdministratorForSystemException() {
        super(NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM);
    }

    public NotOpenAdomAdministratorForSystemException(final Throwable cause) {
        super(NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM, cause);
    }
}