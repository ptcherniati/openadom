package fr.inra.oresing.rest.model.authorization.request;

import fr.inra.oresing.domain.authorization.request.AuthorizationForAll;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.Map;
import java.util.Set;

public class AuthorizationForAllBuilder {

    public AuthorizationForAllBuilder() {
    }

    public AuthorizationForAll build(Map<String, Set<OperationType>> authorizationForAll) {
        return new AuthorizationForAll(
                authorizationForAll
        );
    }
}