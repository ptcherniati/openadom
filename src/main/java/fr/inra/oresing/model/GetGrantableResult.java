package fr.inra.oresing.model;

import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.rest.AuthorizationsResult;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class GetGrantableResult {

    Set<User> users;
    Set<DataGroup> dataGroups;
    Set<AuthorizationScope> authorizationScopes;
    Map<String, ColumnDescription> columnsDescription;
    AuthorizationsResult authorizationsForUser;

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
    public static class ColumnDescription {
        boolean display;
        String title;
        boolean withPeriods;
        boolean withDataGroups;
        Internationalization internationalizationName;
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