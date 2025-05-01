package fr.inra.oresing.rest.model.authorization.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.authorization.request.AuthorizationForAll;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationWithRestriction;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.rest.model.authorization.AuthorizationInput;
import org.apache.commons.collections.MapUtils;

import java.util.*;

public class AuthorizationWithRestrictionBuilder {
  final AuthorizationRequestBuilder authorizationRequestBuilder;

  public AuthorizationWithRestrictionBuilder(final AuthorizationRequestBuilder authorizationRequestBuilder) {
    this.authorizationRequestBuilder = authorizationRequestBuilder;
  }

    public AuthorizationWithRestriction build(
            Map<String, AuthorizationInput> authorizationsByReferences,
            DataRepositoryForBuffer dataRepositoryWithBuffer) {
        Set<String> references = new HashSet<>();
        Map<String, AuthorizationForScope> authorizationWithrestriction = new HashMap<>();
        for (Map.Entry<String, AuthorizationInput> entryByOperation : authorizationsByReferences.entrySet()) {
            String reference = entryByOperation.getKey();
            AuthorizationInput authorizationForReference = entryByOperation.getValue();
            if (MapUtils.isNotEmpty(authorizationForReference.getRequiredAuthorizations()) ||
                    authorizationForReference.getTimeScope()!=null) {
                //TODO catch exception and regroup by referencetype
                authorizationWithrestriction.put(reference, AuthorizationForScope.of(authorizationForReference, dataRepositoryWithBuffer));
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

  private void addAuthorizationForAll(OperationType roleKey,
                                      JsonNode authorizationForAllNode,
                                      ImmutableMap.Builder<OperationType, AuthorizationForAll> authorizationsForAllBuilder,
                                      List<String> badReferences) {
    if (authorizationForAllNode.isEmpty()) {
      return;
    }
    if (authorizationForAllNode.isArray()) {
      authorizationForAllNode.elements().forEachRemaining(referenceNode -> {
        try {

        } catch (Exception e) {

        }
      });
    }
  }
}
