package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.Map;
import java.util.stream.Collectors;

public record AuthorizationWithRestriction(
        Map<String, AuthorizationForScope> authorizationForScope
) {
    public AuthorizationWithRestriction {
        authorizationForScope = authorizationForScope.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            if (entry.getValue().operationTypes().contains(OperationType.publication)) {
                                entry.getValue().operationTypes().add(OperationType.depot);
                            }
                            return entry.getValue();
                        }
                ));
    }
}