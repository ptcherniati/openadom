package fr.inra.oresing.persistence;

import fr.inra.oresing.OreSiException;

public class AuthenticationFailure extends OreSiException {

    public AuthenticationFailure(String message) {
        super(message);
    }

    public AuthenticationFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
