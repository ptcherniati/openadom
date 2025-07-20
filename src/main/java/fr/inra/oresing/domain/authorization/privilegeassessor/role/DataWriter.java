package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.file.FileOrUUID;

public interface DataWriter {
    boolean hasRightForDeposit(FileOrUUID fileOrUUID);

    boolean hasRightForPublishOrUnPublish(FileOrUUID fileOrUUID);
}