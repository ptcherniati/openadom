package fr.inra.oresing.domain.services.file;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public interface BinaryFileService {
    @Transactional()
    UUID storeFile(Application application, MultipartFile file, String comment, BinaryFileDataset binaryFileDataset) throws IOException;


    ReportErrors findPublishedVersion(String nameOrId, String dataType, FileOrUUID params, Set<BinaryFile> filesToStore, boolean searchOverlaps);
}

