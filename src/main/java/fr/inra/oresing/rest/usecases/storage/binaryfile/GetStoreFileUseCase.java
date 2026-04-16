package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.DataWriter;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.data.VersioningService;
import fr.inra.oresing.rest.data.publication.StoreFile;
import org.springframework.stereotype.Component;

@Component
public class GetStoreFileUseCase {

    private final VersioningService versioningService;

    public GetStoreFileUseCase(VersioningService versioningService) {
        this.versioningService = versioningService;
    }

    public StoreFile execute(Application application, String dataName, FileOrUUID fileOrUUID, String fileName, DataWriter applicationDataWriter) {
        return versioningService.getStoreFile(application, dataName, fileOrUUID, fileName, applicationDataWriter);
    }
}
