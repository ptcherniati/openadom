package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Set;
import java.util.UUID;

@Value
public class GetGrantableResult {

    Set<User> users;
    Set<DataGroup> dataGroups;
    Set<AuthorizationScope> authorizationScopes;

    @Value
    public static class User {
        UUID id;
        String label;
    }

    @Value
    public static class DataGroup {
        String id;
        String label;
    }

    @Value
    public static class AuthorizationScope {
        String id;
        String label;
        Set<Option> options;

        @Value
        public static class Option {
            String id;
            String label;
            Set<Option> children;
        }
    }
}
