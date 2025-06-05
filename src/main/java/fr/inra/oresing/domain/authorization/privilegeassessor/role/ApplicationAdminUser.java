package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalRoleToBeGranted;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalUserToBeGranted;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ApplicationAdminUser(
        Application application,
        String dataName
) implements ApplicationManager, ApplicationDataWriter, ApplicationDataDelete {
    public ApplicationAdminUser(Application application) {
        this(application, ApplicationManager.ALL_DATANAMES);
    }

    @Override
    public ApplicationAdminUser canUpdateApplication() {
        return this;
    }

    public void canManagerRightOfUserForRole(OreSiUser user, OreSiRoleForUser roleForUser) {
        if (Optional.ofNullable(user)
                .map(OreSiUser::getChartes)
                .map(chartes -> chartes.get(application().getId().toString()))
                .isEmpty()) {
            throw new IllegalUserToBeGranted(Objects.requireNonNull(user), application().getName());
        }
        OreSiRightOnApplicationRole userManager = OreSiRightOnApplicationRole.userAdminOn(application());
        OreSiRightOnApplicationRole applicationManager = OreSiRightOnApplicationRole.adminOn(application());
        if (!List.of(applicationManager.getAsSqlRole(), userManager.getAsSqlRole()).toString().contains(roleForUser.role())) {
            throw new IllegalRoleToBeGranted(roleForUser.role());
        }
    }

    @Override
    public boolean canDelete(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public boolean hasRightForDeposit(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public OreSiTechnicalException getException() {
        return null;
    }
}