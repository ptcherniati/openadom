package fr.inra.oresing.model;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.rest.AuthorizationsResult;
import lombok.Value;

@Value
public class GetAuthorizationResults {
    ImmutableSet<GetAuthorizationResult> authorizationResults;
    AuthorizationsResult authorizationsForUser;
}