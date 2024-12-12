package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record AuthorizationsForApplicationUser(
        Application application,
        boolean isApplicationManager,
        boolean isUserManager,
        Map<String, List<AuthorizationParsed>> userAuthorizations,
        Map<String, AuthorizationParsed> publicAuthorizations
) {
    public boolean canRead(String dataName) {
        if(isApplicationManager || isUserManager){
            return true;
        }
        return
                Optional.of(userAuthorizations())
                        .map(authorizations -> authorizations.get(dataName))
                        .stream().flatMap(List::stream)
                        .map(AuthorizationParsed::operationTypes)
                        .flatMap(Set::stream)
                        .anyMatch(OperationType.extraction::equals) ||
                        Optional.of(publicAuthorizations())
                                .map(authorizations -> authorizations.get(dataName))
                                .map(AuthorizationParsed::operationTypes)
                                .stream().flatMap(Set::stream)
                                .anyMatch(OperationType.extraction::equals);
    }
}
