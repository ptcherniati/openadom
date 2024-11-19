package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableSet;

import java.util.Set;


public record GetAuthorizationReferencesResults(ImmutableSet<GetAuthorizationReferencesResult> authorizationResults,
                                                AuthorizationsReferencesResult authorizationsForUser,
                                                Set<GetGrantableResult.User> users) {
}