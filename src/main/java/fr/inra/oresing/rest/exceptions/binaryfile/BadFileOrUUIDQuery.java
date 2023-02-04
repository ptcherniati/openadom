package fr.inra.oresing.rest.exceptions.binaryfile;

import fr.inra.oresing.OreSiTechnicalException;

public class BadFileOrUUIDQuery extends OreSiTechnicalException {
    public BadFileOrUUIDQuery(String message) {
        super(message);
    }
}