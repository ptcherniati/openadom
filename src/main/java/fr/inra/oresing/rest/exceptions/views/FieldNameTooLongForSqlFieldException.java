package fr.inra.oresing.rest.exceptions.views;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class FieldNameTooLongForSqlFieldException extends OreSiTechnicalException {
    public FieldNameTooLongForSqlFieldException(final String message) {
        super(message);
    }
}