package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class DisconnectedException extends OreSiTechnicalException {
    public DisconnectedException(final String message) {
        super(message);
    }
}