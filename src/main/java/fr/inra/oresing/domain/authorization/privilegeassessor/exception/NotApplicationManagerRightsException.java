package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationManagerRightsException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_APPLICATION_MANAGEMENT = "NO_RIGHT_FOR_APPLICATION_MANAGEMENT";
    public String applicationName;

    public NotApplicationManagerRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
        this.applicationName = applicationName;
    }

    public NotApplicationManagerRightsException() {
        super(NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
    }

    public NotApplicationManagerRightsException(final Throwable cause) {
        super(NO_RIGHT_FOR_APPLICATION_MANAGEMENT, cause);
    }
}