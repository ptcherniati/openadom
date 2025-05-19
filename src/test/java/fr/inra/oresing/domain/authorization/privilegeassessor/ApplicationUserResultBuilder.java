package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.rest.model.authorization.ApplicationUserResult;

import java.util.Optional;
import java.util.UUID;

public class ApplicationUserResultBuilder {
    String label = "label";
    String email = "email@openadom.fr";
    boolean isApplicationUser = false;
    boolean isActiveApplicationUser = false;
    private boolean isApplicationManager = false;
    private boolean isUserManager = false;
    private UUID applicationId = UUID.randomUUID();
    private UUID id = UUID.randomUUID();

    public static final ApplicationUserResultBuilder builder() {
        return new ApplicationUserResultBuilder();
    }

    public ApplicationUserResult ApplicationUserResultBuilder() {
        return new ApplicationUserResult(
                Optional.of(applicationId).orElse(UUID.randomUUID()),
                Optional.of(id).orElse(UUID.randomUUID()),
                label,
                email,
                isApplicationManager,
                isUserManager,
                isApplicationUser,
                isActiveApplicationUser
        );
    }

    public ApplicationUserResultBuilder withApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    public ApplicationUserResultBuilder withnId(UUID id) {
        this.id = id;
        return this;
    }

    public ApplicationUserResultBuilder isApplicationManager(boolean isApplicationManager) {
        this.isApplicationManager = isApplicationManager;
        return this;
    }

    public ApplicationUserResultBuilder isUserManager(boolean isUserManager) {
        this.isUserManager = isUserManager;
        return this;
    }

    public ApplicationUserResultBuilder isApplicationUser(boolean isApplicationUser) {
        this.isApplicationUser = isApplicationUser;
        return this;
    }

    public ApplicationUserResultBuilder isActiveApplicationUser(boolean isActiveApplicationUser) {
        this.isActiveApplicationUser = isActiveApplicationUser;
        return this;
    }

    public ApplicationUserResultBuilder withLabel(String label) {
        this.label = label;
        return this;
    }

    public ApplicationUserResultBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

}