package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableMap;

import java.util.*;

public record AdditionalFileAuthorizationRequest(
        Map<String, AuthorizationInput> authorizations) {
    public AdditionalFileAuthorizationRequest(Map<String, AuthorizationInput> authorizations) {
        this.authorizations = authorizations==null?null:ImmutableMap.copyOf(authorizations);
    }

    /*public AuthorizationRequest toAuthorizationRequest(Application application,
                                                       List<UUID> allUsers,
                                                       List<OreSiAuthorization> authorizationsForCurrentUser,
                                                       List<AuthorizationRequestError> errors) {
        return new AuthorizationRequestBuilder(
                application,
                allUsers,
                authorizationsForCurrentUser,
                errors
        )
                .build(this);
    }*/
}