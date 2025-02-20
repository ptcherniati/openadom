package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationUserManagerRightsException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT = "NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT";
    public String applicationName;

    public NotApplicationUserManagerRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT);
        this.applicationName = applicationName;
    }

    public NotApplicationUserManagerRightsException() {
        super(NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT);
    }

    public NotApplicationUserManagerRightsException(final Throwable cause) {
        super(NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT, cause);
    }
}