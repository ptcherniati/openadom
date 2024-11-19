package fr.inra.oresing.domain.exceptions.authentication.authentication;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class DisconnectedException extends OreSiTechnicalException {
    public DisconnectedException(final String message) {
        super(message);
    }
}