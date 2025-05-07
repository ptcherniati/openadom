package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationsResultBuilder {
    public static final AuthorizationsResultBuilder builder() {
        return new AuthorizationsResultBuilder();
    }

    private Map<String, List<AuthorizationParsed>> userAuthorization = new HashMap<>();
    private Map<String, AuthorizationParsed> publicAuthorization = new HashMap<>();
    private String applicationName = "applicationName";
    private boolean isApplicationCreator = false;
    private boolean isApplicationManager = false;
    private boolean isUserManager = false;
    private boolean isApplicationUser = false;
    private boolean isActiveAppicationUser = false;

    public AuthorizationsResultBuilder putAuthorizationParsed(String dataName, AuthorizationParsed authorizationParsed) {
        this.userAuthorization.compute(dataName,
                        (k, v) -> CollectionUtils.isNotEmpty(v) ? v : new ArrayList<>())
                .add(authorizationParsed);
        return this;
    }

    public AuthorizationsResultBuilder withPublicAuthorization(String dataName, AuthorizationParsed authorizationParsed) {
        this.publicAuthorization.put(dataName, authorizationParsed);
        return this;
    }

    public AuthorizationsResultBuilder withIsApplicationCreator(boolean isApplicationCreator) {
        this.isApplicationCreator = isApplicationCreator;
        return this;
    }

    public AuthorizationsResultBuilder withIsApplicationManager(boolean isApplicationManager) {
        this.isApplicationManager = isApplicationManager;
        return this;
    }

    public AuthorizationsResultBuilder withIsUserManager(boolean isUserManager) {
        this.isUserManager = isUserManager;
        return this;
    }

    public AuthorizationsResultBuilder withIsApplicationUser(boolean isApplicationUser) {
        this.isApplicationUser = isApplicationUser;
        return this;
    }

    public AuthorizationsResultBuilder withIsActiveAppicationUser(boolean isActiveAppicationUser) {
        this.isActiveAppicationUser = isActiveAppicationUser;
        return this;
    }

    public AuthorizationsResultBuilder withApplicationName(String applicationId, String applicationName) {
        this.applicationName = applicationName;
        return this;
    }

    public AuthorizationsResult build() {
        return new AuthorizationsResult(
                userAuthorization,
                publicAuthorization,
                applicationName,
                isApplicationCreator,
                isApplicationManager,
                isUserManager,
                isApplicationUser,
                isActiveAppicationUser
        );
    }
}