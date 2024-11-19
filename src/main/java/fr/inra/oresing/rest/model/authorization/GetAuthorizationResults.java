package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableSet;


public record GetAuthorizationResults(ImmutableSet<GetAuthorizationResult> authorizationResults,
                                      AuthorizationsResult authorizationsForUser) {
}