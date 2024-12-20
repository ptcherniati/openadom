package fr.inra.oresing.rest.model.authorization.request;

import fr.inra.oresing.domain.authorization.request.AuthorizationForAll;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.*;

public class AuthorizationForAllBuilder {

    public static final String AUTHORIZATION_FOR_ALL = "authorizationForAll";
    final AuthorizationRequestBuilder authorizationRequestBuilder;

    public AuthorizationForAllBuilder(final AuthorizationRequestBuilder authorizationRequestBuilder) {
        this.authorizationRequestBuilder = authorizationRequestBuilder;
    }

    public AuthorizationForAll build(Map<String, Set<OperationType>> authorizationForAll) {
        return new AuthorizationForAll(
                authorizationForAll
        );
    }
}
