package fr.inra.oresing.domain.services.file;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.additionalfiles.AdditionalBinaryFileResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BinaryFileService {
    @Transactional()
    UUID storeFile(Application application, MultipartFile file, String comment, BinaryFileDataset binaryFileDataset) throws IOException;


    Optional<BinaryFile> getFile(String applicationNameOrID, UUID id);

    Optional<BinaryFile> getFileWithData(String applicationNameOrID, UUID id);

    @Transactional
    Optional<UUID> removeFile(Application application, UUID id);

    ReportErrors findPublishedVersion(String nameOrId, String dataType, FileOrUUID params, Set<BinaryFile> filesToStore, boolean searchOverlaps);

    List<BinaryFile> getFilesOnRepository(
            String nameOrId,
            String datatype,
            BinaryFileDataset fileDatasetID,
            boolean overlap
    );

    AdditionalBinaryFileResult getAdditionalBinaryFileResult(
            AdditionalBinaryFile additionalBinaryFile);
}