package fr.inra.oresing.rest;

import fr.inra.oresing.model.Authorization;
import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.*;

@Value
public class GetGrantableResult {

    Set<User> users;
    Map<String, Set<DataGroup>> dataGroups;
    Map<String, Set<AuthorizationScope>> authorizationScopes;
    Map<String, SortedMap<String, ColumnDescription>> columnsDescription;
    AuthorizationsResult authorizationsForUser;
    Map<String, Map<OperationType, List<Authorization>>> publicAuthorizations;

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
        boolean forPublic;
        boolean forRequest;
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