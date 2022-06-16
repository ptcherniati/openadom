package fr.inra.oresing.rest.exceptions.authentication;

import fr.inra.oresing.OreSiTechnicalException;

public class DisconnectedException extends OreSiTechnicalException {
    public DisconnectedException(String message) {
        super(message);
    }
}