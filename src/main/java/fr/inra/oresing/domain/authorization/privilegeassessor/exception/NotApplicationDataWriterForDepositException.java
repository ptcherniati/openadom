package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationDataWriterForDepositException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT = "NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT";
    public String applicationName;
    public String dataName;

    public NotApplicationDataWriterForDepositException(final String applicationName, String dataName) {
        super(NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT);
        this.applicationName = applicationName;
        this.dataName = dataName;
    }
}