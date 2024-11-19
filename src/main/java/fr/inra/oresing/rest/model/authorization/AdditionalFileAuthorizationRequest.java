package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.authorization.request.AuthorizationRequestBuilder;

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