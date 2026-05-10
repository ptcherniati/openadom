package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationsForApplicationUserFactory {
    private List<String> roles;
    private Application application;
    private boolean isApplicationManager = false;
    private boolean isUserManager = false;
    private Map<String, List<AuthorizationParsed>> userAuthorizations = new HashMap<>();
    private Map<String, AuthorizationParsed> publicAuthorization = new HashMap<>();

    public static AuthorizationsForApplicationUserFactory builder() {
        return new AuthorizationsForApplicationUserFactory();
    }

    public AuthorizationsForApplicationUserFactory withRoles(List<String> roles) {
        this.roles = roles;
        return this;
    }

    public AuthorizationsForApplicationUserFactory withApplication(Application application) {
        this.application = application;
        return this;
    }

    public AuthorizationsForApplicationUserFactory withIsApplicationManager(boolean isApplicationManager) {
        this.isApplicationManager = isApplicationManager;
        return this;
    }

    public AuthorizationsForApplicationUserFactory withIsUserManager(boolean isUserManager) {
        this.isUserManager = isUserManager;
        return this;
    }

    public AuthorizationsForApplicationUserFactory withUserAuthorizations(Map<String, List<AuthorizationParsed>> userAuthorizations) {
        this.userAuthorizations = userAuthorizations;
        return this;
    }

    public AuthorizationsForApplicationUserFactory withPublicAuthorization(Map<String, AuthorizationParsed> publicAuthorizations) {
        this.publicAuthorization = publicAuthorizations;
        return this;
    }

    public AuthorizationsForApplicationUser build() {
        return new AuthorizationsForApplicationUser(
                roles,
                application,
                isApplicationManager,
                isUserManager,
                userAuthorizations,
                publicAuthorization
        );
    }
}