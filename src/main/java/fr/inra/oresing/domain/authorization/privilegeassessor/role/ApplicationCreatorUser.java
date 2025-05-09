package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;

import java.util.Set;
import java.util.regex.Pattern;

public record ApplicationCreatorUser(
        Set<String> applicationCreatorPatterns) implements ApplicationCreator {
    public static final String APPLICATION_CREATOR_ROLE = "applicationCreator";
    @Override
    public boolean canCreateApplication(String applicationName) {
        if (applicationCreatorPatterns()
                .stream()
                .map(Pattern::compile)
                .map(Pattern::asMatchPredicate)
                .noneMatch(predicate -> predicate.test(applicationName))
        ) {
            throw new NotApplicationCreatorRightsException(applicationName, applicationCreatorPatterns());
        }
        return false;
    }
}