package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiTechnicalException;

public class BadFileOrUUIDQuery extends OreSiTechnicalException {
    public BadFileOrUUIDQuery(String message) {
        super(message);
    }
}