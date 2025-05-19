package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationInput;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public sealed interface AuthorizationForScope permits AuthorizationNoRestriction, AuthorizationForReferenceScope, AuthorizationForTimeScope, AuthorizationForReferenceScopeAndTimeScope {
    static AuthorizationForScope of(AuthorizationInput authorization) {
        Map<String, List<Ltree>> authorizationScope = Map.of();
        if (MapUtils.isNotEmpty(authorization.getRequiredAuthorizations())) {
            authorizationScope = authorization.getRequiredAuthorizations();
        }

        if (MapUtils.isEmpty(authorization.getRequiredAuthorizations()) && authorization.getTimeScope() == null) {
            return new AuthorizationNoRestriction(authorization.getOperationTypes());
        }
        if (authorization.getTimeScope() == null || LocalDateTimeRange.always().equals(authorization.getTimeScope())) {
            return new AuthorizationForReferenceScope(authorization.getOperationTypes(), authorizationScope);
        }
        if (authorization.getRequiredAuthorizations() == null) {
            return new AuthorizationForTimeScope(authorization.getOperationTypes(), authorization.getTimeScope());
        }
        return new AuthorizationForReferenceScopeAndTimeScope(
                authorization.getOperationTypes(),
                authorizationScope,
                authorization.getTimeScope());

    }

    Set<OperationType> operationTypes();

    default LocalDateTimeRange timeScope() {
        return null;
    }

    default Map<String, List<Ltree>> authorizationScope() {
        return Map.of();
    }

}