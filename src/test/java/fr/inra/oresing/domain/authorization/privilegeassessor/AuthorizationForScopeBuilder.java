package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.*;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

public class AuthorizationForScopeBuilder {
    public static final AuthorizationForScopeBuilder builder() {
        return new AuthorizationForScopeBuilder();
    }

    Set<OperationType> operationTypes = new HashSet<>();
    Map<String, List<Ltree>> authorizationScopes = Map.of();
    LocalDateTimeRange timeScope = LocalDateTimeRange.always();

    // AuthorizationNoRestriction,
    // AuthorizationForReferenceScope,
    // AuthorizationForTimeScope,
    // AuthorizationForReferenceScopeAndTimeScope
    public AuthorizationForScope forAuthorizationForNoRestriction() {
        return new AuthorizationNoRestriction(
                operationTypes
        );
    }

    public AuthorizationForScope forAuthorizationForReferenceScope() {
        return new AuthorizationForReferenceScope(
                operationTypes,
                authorizationScopes
        );
    }

    public AuthorizationForScope forAuthorizationForTimeScope() {
        return new AuthorizationForTimeScope(
                operationTypes,
                timeScope
        );
    }

    public AuthorizationForScope forAuthorizationForReferenceScopeAndTimeScope() {
        return new AuthorizationForReferenceScopeAndTimeScope(
                operationTypes,
                authorizationScopes,
                timeScope
        );
    }

    public AuthorizationForScopeBuilder withOperationTypes(Set<OperationType> operationTypes) {
        this.operationTypes = operationTypes;
        return this;
    }

    public AuthorizationForScopeBuilder withOperationType(OperationType operationType) {
        this.operationTypes.add(operationType);
        return this;
    }

    public AuthorizationForScopeBuilder withAuthorizationScopes(Map<String, List<Ltree>> authorizationScopes) {
        this.authorizationScopes = authorizationScopes;
        return this;
    }

    public AuthorizationForScopeBuilder withAuthorizationScope(String dataName, Ltree authorizationScope) {
        this.authorizationScopes.compute(dataName,
                        (k, v) -> CollectionUtils.isNotEmpty(v) ? v : new ArrayList<>())
                .add(authorizationScope);
        return this;
    }

    public AuthorizationForScopeBuilder withAuthorizationScope(String dataName, List<Ltree> authorizationScopes) {
        this.authorizationScopes.put(dataName, authorizationScopes);
        return this;
    }

    public AuthorizationForScopeBuilder withTimeScope(LocalDateTimeRange timeScope) {
        this.timeScope = timeScope;
        return this;
    }
}