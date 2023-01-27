package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import lombok.Value;

@Value
public class GetAuthorizationReferencesResults {
    ImmutableSet<GetAuthorizationReferencesResult> authorizationResults;
    AuthorizationsReferencesResult authorizationsForUser;
}