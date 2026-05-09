package fr.inra.oresing.domain.exceptions;

public class FieldNameTooLongForSqlFieldException extends OreSiTechnicalException {
    public FieldNameTooLongForSqlFieldException(final String message) {
        super(message);
    }
}