package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthorizationForAll(Map<String, Set<OperationType>> authorizationForAll) {
    public AuthorizationForAll(Map<String, Set<OperationType>> authorizationForAll) {
        authorizationForAll = authorizationForAll.entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> {
                                    if(e.getValue().contains(OperationType.publication)){
                                        e.getValue().add(OperationType.depot);
                                    }
                                    return e.getValue();
                                }
                        )
                );

        this.authorizationForAll = authorizationForAll;
    }
}
