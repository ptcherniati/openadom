package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.Set;

public record AuthorizationNoRestriction(Set<OperationType> operationTypes) implements AuthorizationForScope {
}
