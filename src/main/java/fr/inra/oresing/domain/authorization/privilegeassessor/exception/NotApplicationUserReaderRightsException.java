package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationUserReaderRightsException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION = "NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION";
    public String applicationName;

    public NotApplicationUserReaderRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION);
        this.applicationName = applicationName;
    }

    public NotApplicationUserReaderRightsException() {
        super(NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION);
    }

    public NotApplicationUserReaderRightsException(final Throwable cause) {
        super(NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION, cause);
    }
}