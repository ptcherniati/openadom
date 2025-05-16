package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;

import java.util.*;
import java.util.stream.Collectors;

public record AuthorizationsForApplicationUser(
        List<String> roles,
        Application application,
        boolean isApplicationManager,
        boolean isUserManager,
        Map<String, List<AuthorizationParsed>> userAuthorizations,
        Map<String, AuthorizationParsed> publicAuthorizations
) {
    public boolean canRead(String dataName) {
        return canDoAction(dataName, Set.of(OperationType.depot, OperationType.publication, OperationType.extraction, OperationType.delete));
    }

    /* find if exists authorization foroperationtype of action */
    private boolean canDoAction(String dataName, Set<OperationType> actions) {
        if (isApplicationManager || isUserManager) {
            return true;
        }
        return Optional.of(userAuthorizations())
                .map(authorizations -> authorizations.get(dataName))
                .stream().flatMap(List::stream)
                .map(AuthorizationParsed::operationTypes)
                .flatMap(Set::stream)
                .anyMatch(actions::contains) ||
                Optional.of(publicAuthorizations())
                        .map(authorizations -> authorizations.get(dataName))
                        .map(AuthorizationParsed::operationTypes)
                        .stream().flatMap(Set::stream)
                        .anyMatch(actions::contains);
    }

    public ArrayList<AuthorizationParsed> getAuthorizations(String dataName, Set<OperationType> actions) {
        ArrayList<AuthorizationParsed> parsedAuthorisationForActions = Optional.of(userAuthorizations())
                .map(authorizations -> authorizations.get(dataName))
                .stream().flatMap(List::stream)
                .filter(authorizationParsed -> authorizationParsed.operationTypes().stream().anyMatch(actions::contains))
                .collect(Collectors.toCollection(ArrayList::new));
        Optional.of(publicAuthorizations())
                .map(authorizations -> authorizations.get(dataName))
                .filter(authorizationParsed -> authorizationParsed.operationTypes().stream().anyMatch(actions::contains))
                .ifPresent(parsedAuthorisationForActions::add);
        return parsedAuthorisationForActions;
    }

    public boolean canWrite(String dataName, boolean toPublish) {
        if (isApplicationManager || isUserManager) {
            return true;
        }
        return canDoAction(dataName, toPublish ? Set.of(OperationType.publication) : Set.of(OperationType.depot));
    }

    public boolean canDelete(String dataName, boolean isRepository) {
        if (isApplicationManager || isUserManager) {
            return true;
        }
        return canDoAction(dataName, isRepository ? Set.of(OperationType.publication, OperationType.delete) : Set.of(OperationType.publication));
    }
}