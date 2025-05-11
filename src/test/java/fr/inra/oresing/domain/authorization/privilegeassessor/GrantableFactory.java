package fr.inra.oresing.domain.authorization.privilegeassessor;

import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.rest.model.authorization.ApplicationUserResult;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.*;

public class GrantableFactory {
    private Set<ApplicationUserResult> users = new HashSet<>();
    private AuthorizationsResult authorizationsResult;
    private final Map<String, List<GetGrantableResult.ReferenceScope>> refenceScopes = new HashMap<>();
    private final Map<String, List<AuthorizationForScope>> publicAuthorizations = new HashMap<>();
    private final Map<String, SortedMap<String, GetGrantableResult.ColumnDescription>> columnDesriptions = new HashMap<>();

    static GrantableFactory builder() {
        return new GrantableFactory();
    }

    public GetGrantableResult build() {
        return new GetGrantableResult(
                ImmutableSortedSet.copyOf(users),
                refenceScopes,
                columnDesriptions,
                authorizationsResult,
                publicAuthorizations
        );
    }

    public GrantableFactory withUsers(Set<ApplicationUserResult> users) {
        this.users = users;
        return this;
    }

    public GrantableFactory withUser(ApplicationUserResult user) {
        this.users.add(user);
        return this;
    }

}