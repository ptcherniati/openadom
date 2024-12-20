package fr.inra.oresing.rest.data.publication;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.file.BinaryFileService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CheckRights(
        AuthorizationPublicationService builder) implements State {
    public FileOrUUID checkPublicationRights(
            Set<BinaryFile> filesToStore,
            DataRepository dataRepository,
            BinaryFileService binaryFileService,
            BinaryFileRepository binaryFileRepository) {
        if (!builder().isRepository() || params().topublish()) {

            StandardDataDescription dataDescription = application().findData(builder().dataName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no dataDescription for %s".formatted(builder().dataName)));
            InvalidDatasetContentException.checkErrorsIsEmpty(binaryFileService.findPublishedVersion(
                    application().getName(),
                    dataName(),
                    params(),
                    filesToStore,
                    true));
            assert builder().hasRightForPublishOrUnPublish();


            Preconditions.checkArgument(binaryFile() != null || (params() != null && params().fileid() != null), "le fichier ou params.fileid est requis");
            Preconditions.checkArgument(!(binaryFile().getFileData().length == 0), "le CSV téléversé pour le référentiel " + dataName() + " est vide");
            UUID fileId = binaryFileRepository.store(binaryFile());
            return FileOrUUID.from(params(), fileId);
        } else {
            return FileOrUUID.from(params(), null);
        }

    }
}
