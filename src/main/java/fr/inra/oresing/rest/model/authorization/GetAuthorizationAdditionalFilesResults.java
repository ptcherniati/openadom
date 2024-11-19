package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.additionalfiles.AuthorizationsAdditionalFilesResult;

import java.util.Set;

public record GetAuthorizationAdditionalFilesResults(
        ImmutableSet<GetAuthorizationAdditionalFilesResult> authorizationResults,
        AuthorizationsAdditionalFilesResult authorizationsForUser, Set<GetGrantableResult.User> users) {
}