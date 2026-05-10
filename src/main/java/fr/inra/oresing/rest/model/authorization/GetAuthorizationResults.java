package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.authorization.AuthorizationsResult;


public record GetAuthorizationResults(ImmutableSet<GetAuthorizationResult> authorizationResults,
                                      AuthorizationsResult authorizationsForUser) {
}