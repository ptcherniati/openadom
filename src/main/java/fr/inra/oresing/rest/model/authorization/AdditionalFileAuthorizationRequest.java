package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public record AdditionalFileAuthorizationRequest(
        Map<String, AuthorizationInput> authorizations) {
    public AdditionalFileAuthorizationRequest(Map<String, AuthorizationInput> authorizations) {
        this.authorizations = authorizations == null ? null : ImmutableMap.copyOf(authorizations);
    }
}