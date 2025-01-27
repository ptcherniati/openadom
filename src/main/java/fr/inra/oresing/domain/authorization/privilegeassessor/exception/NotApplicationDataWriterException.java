package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationDataWriterException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_USER_DATA_WRITER = "NO_RIGHT_FOR_USER_DATA_WRITER";
    public String applicationName;
    public String dataName;

    public NotApplicationDataWriterException(final String applicationName, String dataName) {
        super(NO_RIGHT_FOR_USER_DATA_WRITER);
        this.applicationName = applicationName;
        this.dataName = dataName;
    }

    public NotApplicationDataWriterException() {
        super(NO_RIGHT_FOR_USER_DATA_WRITER);
    }

    public NotApplicationDataWriterException(final Throwable cause) {
        super(NO_RIGHT_FOR_USER_DATA_WRITER, cause);
    }
}