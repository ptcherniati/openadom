package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record AuthorizationForReferenceScope(
        Set<OperationType> operationTypes,
        Map<String, List<Ltree>> authorizationScope
)
        implements AuthorizationForScope {
}
