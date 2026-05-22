package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.authorization.request.AuthorizationInput;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record CreateAuthorizationRequest(
        UUID uuid,
        String name,
        String description,
        Set<UUID> usersId,
        Map<String, Set<OperationType>> authorizationForAll,
        Map<String, AuthorizationInput> authorizationsWithRestriction) {
    public CreateAuthorizationRequest(UUID uuid,
                                      String name,
                                      String description,
                                      Set<UUID> usersId,
                                      Map<String, Set<OperationType>> authorizationForAll,
                                      Map<String, AuthorizationInput> authorizationsWithRestriction) {
        Objects.requireNonNull(name);
        if (name.isEmpty()) {
            throw new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.NO_AUTHORIZATION_NAME,
                    Map.of()
            );
        }
        this.uuid = uuid;
        this.name = name;
        this.description = description;
        this.usersId = usersId == null ? Set.of() : ImmutableSet.copyOf(usersId);
        this.authorizationForAll = authorizationForAll == null ? null : ImmutableMap.copyOf(authorizationForAll);
        this.authorizationsWithRestriction = authorizationsWithRestriction == null ? null : ImmutableMap.copyOf(
                authorizationsWithRestriction.entrySet().stream()
                        .filter(entry -> authorizationForAll == null || !authorizationForAll.containsKey(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    public CreateAuthorizationRequest addDependantAuthorizations(Set<String> dependantsNodes) {
        if (dependantsNodes.isEmpty()) {
            return this;
        }
        Map<String, Set<OperationType>> localAuthorizationForAll = Optional.ofNullable(authorizationForAll)
                .map(HashMap::new)
                .orElseGet(HashMap::new);
        dependantsNodes
                .forEach(dataName -> {
                            Optional<Set<OperationType>> operationTypes = Optional.ofNullable(authorizationsWithRestriction())
                                    .map(authorizationsWithRestriction -> authorizationsWithRestriction.get(dataName))
                                    .map(AuthorizationInput::getOperationTypes);
                            if (operationTypes.isPresent()) {
                                operationTypes.get().add(OperationType.extraction);
                            } else {
                                localAuthorizationForAll
                                        .computeIfAbsent(dataName, k -> new HashSet<>())
                                        .add(OperationType.extraction);
                            }
                        }
                );

        return new CreateAuthorizationRequest(
                uuid(),
                name(),
                description(),
                usersId(),
                localAuthorizationForAll,
                authorizationsWithRestriction()
        );
    }

    public CreateAuthorizationRequest addRequiredOperationTypes(Function<String, Boolean> isVersionningStrategy) {
        return new CreateAuthorizationRequest(
                uuid(),
                name(),
                description(),
                usersId(),
                authorizationForAllWithDependants(isVersionningStrategy),
                authorizationsWithRestrictionWithDependants(isVersionningStrategy)
        );
    }

    private Map<String, AuthorizationInput> authorizationsWithRestrictionWithDependants(Function<String, Boolean> isVersionningStrategy) {
        if(authorizationsWithRestriction()==null){
            return Map.of();
        }
        return authorizationsWithRestriction().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().withRestrictionWithDependants(entry.getKey(), isVersionningStrategy)
                        )
                );
    }

    private Map<String, Set<OperationType>> authorizationForAllWithDependants(Function<String, Boolean> isVersionningStrategy) {
        if(authorizationForAll() == null){
            return Map.of();
        }
        return authorizationForAll().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .flatMap(operationType -> {
                                    final Boolean isVersionning = isVersionningStrategy.apply(entry.getKey());
                                    if(operationType==null){
                                        return Stream.of();
                                    }
                                    if(OperationType.extraction.equals(operationType)) {
                                        return Stream.of(operationType);
                                    }
                                    if(Set.of(OperationType.depot, OperationType.publication).contains(operationType)){
                                        return isVersionning?
                                                Stream.of(OperationType.depot, OperationType.publication, OperationType.delete, OperationType.extraction):
                                                Stream.of(OperationType.depot, OperationType.publication, OperationType.extraction);
                                    }
                                    return Stream.of(OperationType.depot, OperationType.publication, OperationType.delete, OperationType.extraction);
                                })
                                .collect(Collectors.toSet())
                ));
    }
}