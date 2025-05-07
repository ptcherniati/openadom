package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.model.authorization.ApplicationUserResult;

import java.util.Optional;
import java.util.UUID;

public class ApplicationUserResultBuilder {
    private boolean isApplicationManager = false;
    private boolean isUserManager = false;
    private UUID applicationId = UUID.randomUUID();
    private UUID id = UUID.randomUUID();
    String label = "label";
    String email = "email@openadom.fr";
    boolean isApplicationUser = false;
    boolean isActiveApplicationUser = false;

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

    public static final ApplicationUserResultBuilder builder(Application application) {
        return new ApplicationUserResultBuilder();
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
    public ApplicationUserResultBuilder withEmail(String email){
        this.email = email;
        return this;
    }

}