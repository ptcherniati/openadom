package fr.inra.oresing.domain.exceptions.binaryfile.binaryfile;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class BadFileOrUUIDQuery extends OreSiTechnicalException {
    public BadFileOrUUIDQuery(final String message) {
        super(message);
    }
}