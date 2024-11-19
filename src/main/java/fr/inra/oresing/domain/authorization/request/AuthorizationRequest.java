package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.repository.authorization.OperationType;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public record AuthorizationRequest(UUID authorizationId,
                                   String name,
                                   String description,
                                   UUID applicationId,
                                   Set<UUID> userId,
                                   AuthorizationForAll authorizationForAll,
                                   AuthorizationWithRestriction authorizationWithRestriction) {
    public Map<String, AuthorizationForScope> buildAuthorizationsByDataname() {
        Map<String, AuthorizationForScope> authorizationBydataName = buildAuthorizationForAll();
        Optional.ofNullable(authorizationWithRestriction)
                .map(AuthorizationWithRestriction::authorizationForScope)
                .ifPresent(authorizationBydataName::putAll);
        return authorizationBydataName;
    }

    private Map<String, AuthorizationForScope> buildAuthorizationForAll() {
        if (authorizationForAll == null || MapUtils.isEmpty(authorizationForAll.authorizationForAll())) {
            return new HashMap<>();
        }
        Map<String, AuthorizationForScope> buildAuthorizationsByReferences = new HashMap<>();
        return authorizationForAll.authorizationForAll().entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> new AuthorizationNoRestriction(entry.getValue())
                        )
                );
    }
}
