package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthorizationWithRestriction(
        Map<String, AuthorizationForScope> authorizationForScope
)  {
    public AuthorizationWithRestriction(Map<String, AuthorizationForScope> authorizationForScope) {
        authorizationForScope = authorizationForScope.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry->{
                            if(entry.getValue().operationTypes().contains(OperationType.publication)){
                                entry.getValue().operationTypes().add(OperationType.depot);
                            }
                            return entry.getValue();
                        }
                ));
        this.authorizationForScope = authorizationForScope;
    }
}
