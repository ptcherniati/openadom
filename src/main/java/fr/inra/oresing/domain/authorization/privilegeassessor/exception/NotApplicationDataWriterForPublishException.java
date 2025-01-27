package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationDataWriterForPublishException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_USER_DATA_WRITER_FOR_PUBLISH = "NO_RIGHT_FOR_USER_DATA_WRITER_FOR_PUBLISH";
    public String applicationName;
    public String dataName;

    public NotApplicationDataWriterForPublishException(final String applicationName, String dataName) {
        super(NO_RIGHT_FOR_USER_DATA_WRITER_FOR_PUBLISH);
        this.applicationName = applicationName;
        this.dataName = dataName;
    }

    public NotApplicationDataWriterForPublishException() {
        super(NO_RIGHT_FOR_USER_DATA_WRITER_FOR_PUBLISH);
    }

    public NotApplicationDataWriterForPublishException(final Throwable cause) {
        super(NO_RIGHT_FOR_USER_DATA_WRITER_FOR_PUBLISH, cause);
    }
}