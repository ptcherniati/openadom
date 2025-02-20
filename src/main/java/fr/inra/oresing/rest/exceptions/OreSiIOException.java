package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiException;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class OreSiIOException extends OreSiTechnicalException {
    private static final String CANT_LOAD_FILE = "CANT_LOAD_FILE";
    public static OreSiIOException ORE_SI_IOEXCEPTION_CANT_LOAD_FILE() {
        return new OreSiIOException(CANT_LOAD_FILE);
    }
    public OreSiIOException(String message) {
        super(message);
    }
}
