package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record AuthorizationForAll(Map<String, Set<OperationType>> authorizationForAll) {
    public AuthorizationForAll {
        authorizationForAll = authorizationForAll.entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> {
                                    if (e.getValue().contains(OperationType.publication)) {
                                        e.getValue().add(OperationType.depot);
                                    }
                                    if (e.getValue().contains(OperationType.depot) || e.getValue().contains(OperationType.delete)) {
                                        e.getValue().add(OperationType.extraction);
                                    }
                                    return e.getValue();
                                }
                        )
                );

    }
}
