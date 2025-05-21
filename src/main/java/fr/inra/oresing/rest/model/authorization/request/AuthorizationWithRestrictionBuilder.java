package fr.inra.oresing.rest.model.authorization.request;

import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationWithRestriction;
import fr.inra.oresing.rest.model.authorization.AuthorizationInput;
import org.apache.commons.collections.MapUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AuthorizationWithRestrictionBuilder {
    final AuthorizationRequestBuilder authorizationRequestBuilder;

    public AuthorizationWithRestrictionBuilder(final AuthorizationRequestBuilder authorizationRequestBuilder) {
        this.authorizationRequestBuilder = authorizationRequestBuilder;
    }

    public AuthorizationWithRestriction build(
            Map<String, AuthorizationInput> authorizationsByReferences) {
        Set<String> references = new HashSet<>();
        Map<String, AuthorizationForScope> authorizationWithrestriction = new HashMap<>();
        for (Map.Entry<String, AuthorizationInput> entryByOperation : authorizationsByReferences.entrySet()) {
            String reference = entryByOperation.getKey();
            AuthorizationInput authorizationForReference = entryByOperation.getValue();
            if (MapUtils.isNotEmpty(authorizationForReference.getRequiredAuthorizations()) ||
                    authorizationForReference.getTimeScope() != null) {
                authorizationWithrestriction.put(reference, AuthorizationForScope.of(authorizationForReference));
            }
        }
        if (!authorizationRequestBuilder.existsReferences(references)) {
            return null;
        }
        if (MapUtils.isEmpty(authorizationWithrestriction)) {
            return null;
        }
        return new AuthorizationWithRestriction(authorizationWithrestriction);
    }

}