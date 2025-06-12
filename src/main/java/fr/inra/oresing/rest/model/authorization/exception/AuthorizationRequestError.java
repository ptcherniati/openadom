package fr.inra.oresing.rest.model.authorization.exception;

import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;

import java.util.Map;

public class AuthorizationRequestError extends Throwable {
    Map<String, Object> params;

    public AuthorizationRequestError(final AuthorizationRequestException message, final Map<String, Object> params) {
        super(message.getMessage());
        this.params = params;
    }

    public AuthorizationRequestError(final String message) {
        super(message);
    }
}