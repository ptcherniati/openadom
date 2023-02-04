package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import lombok.Value;

import java.util.Set;

@Value
public class GetAuthorizationReferencesResults {
    ImmutableSet<GetAuthorizationReferencesResult> authorizationResults;
    AuthorizationsReferencesResult authorizationsForUser;
    Set<GetGrantableResult.User> users;
}