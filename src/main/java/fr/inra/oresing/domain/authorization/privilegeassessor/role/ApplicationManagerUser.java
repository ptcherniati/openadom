package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.FileOrUUID;

public record ApplicationManagerUser(
        Application application,
        String dataName
) implements ApplicationManager, ApplicationDataWriter, ApplicationDataDelete {

    public ApplicationManagerUser(Application application) {
        this(application, ApplicationManager.ALL_DATANAMES);
    }


    @Override
    public boolean canDelete(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID) {
        return false;
    }

    @Override
    public boolean hasRightForDeposit(FileOrUUID fileOrUUID) {
        return true;
    }

    @Override
    public OreSiTechnicalException getException() {
        return null;
    }


    @Override
    public ApplicationManagerUser canUpdateApplication() {
        return this;
    }

}