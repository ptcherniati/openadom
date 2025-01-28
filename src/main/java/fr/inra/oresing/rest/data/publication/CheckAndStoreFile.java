package fr.inra.oresing.rest.data.publication;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.file.BinaryFileService;

import java.util.Set;
import java.util.UUID;

public record CheckAndStoreFile(
        AuthorizationPublicationService builder) implements State {
    public FileOrUUID checkAndStoreFile(
            Set<BinaryFile> filesToStore,
            BinaryFileService binaryFileService,
            BinaryFileRepository binaryFileRepository) {
        if (!builder().isRepository() || fileOrUuid().topublish()) {
            InvalidDatasetContentException.checkErrorsIsEmpty(binaryFileService.findPublishedVersion(
                    application().getName(),
                    dataName(),
                    fileOrUuid(),
                    filesToStore,
                    true));
            Preconditions.checkArgument(binaryFile() != null || (fileOrUuid() != null && fileOrUuid().fileid() != null), "le fichier ou params.fileid est requis");
            Preconditions.checkArgument(!(binaryFile().getFileData().length == 0), "le CSV téléversé pour le référentiel " + dataName() + " est vide");
            UUID fileId = binaryFileRepository.store(binaryFile());
            return FileOrUUID.from(fileOrUuid(), fileId);
        } else {
            return FileOrUUID.from(fileOrUuid(), null);
        }

    }
}
