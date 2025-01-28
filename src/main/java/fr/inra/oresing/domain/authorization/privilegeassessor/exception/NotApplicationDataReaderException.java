package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationDataReaderException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_USER_DATA_READER = "NO_RIGHT_FOR_USER_DATA_READER";
    public String applicationName;
    public String dataName;

    public NotApplicationDataReaderException(final String applicationName, String dataName) {
        super(NO_RIGHT_FOR_USER_DATA_READER);
        this.applicationName = applicationName;
        this.dataName = dataName;
    }

    public NotApplicationDataReaderException() {
        super(NO_RIGHT_FOR_USER_DATA_READER);
    }

    public NotApplicationDataReaderException(final Throwable cause) {
        super(NO_RIGHT_FOR_USER_DATA_READER, cause);
    }
}