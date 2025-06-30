package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record AuthorizationRequest(UUID authorizationId,
                                   String name,
                                   String description,
                                   UUID applicationId,
                                   Set<UUID> userId,
                                   AuthorizationForAll authorizationForAll,
                                   AuthorizationWithRestriction authorizationWithRestriction) {
    public Map<String, AuthorizationForScope> buildAuthorizationsByDataname(List<String> noDataOrInsertionStrategyList) {
        Map<String, AuthorizationForScope> authorizationBydataName = buildAuthorizationForAll(noDataOrInsertionStrategyList);
        Optional.ofNullable(authorizationWithRestriction)
                .map(AuthorizationWithRestriction::authorizationForScope)
                .ifPresent(authorizationBydataName::putAll);
        return authorizationBydataName;
    }

    private Map<String, AuthorizationForScope> buildAuthorizationForAll(List<String> noDataOrInsertionStrategyList) {
        if (authorizationForAll == null || MapUtils.isEmpty(authorizationForAll.authorizationForAll())) {
            return new HashMap<>();
        }
        return authorizationForAll.authorizationForAll().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry ->
                                new AuthorizationNoRestriction(entry.getValue().stream()
                                        .flatMap(restriction -> {
                                            if (!noDataOrInsertionStrategyList.contains(entry.getKey())) {
                                                return Stream.of(restriction);
                                            }
                                            if (Set.of(OperationType.depot, OperationType.publication).contains(restriction)) {
                                                return Stream.of(OperationType.depot, OperationType.publication);
                                            }
                                            return Stream.of(restriction);
                                        })
                                        .collect(Collectors.toSet())
                                )
                        )
                );
    }
}