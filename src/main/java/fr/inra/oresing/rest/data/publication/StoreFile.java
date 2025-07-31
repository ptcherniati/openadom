package fr.inra.oresing.rest.data.publication;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForPublishException;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.rest.exceptions.OreSiIOException;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

public record StoreFile(AuthorizationPublicationService builder) implements State {

    public State loadOrCreateFile(
            DataFile file,
            BinaryFileRepository binaryFileRepository,
            BinaryFileService binaryFileService
    ) throws FileNotFoundException {

        InputStream inputStream = file == null ? null : file.inputStream();
        builder().binaryFile = Optional.ofNullable(fileOrUuid()).map(FileOrUUID::fileid)
                .flatMap(binaryFileRepository::tryFindByIdWithData)
                .orElseGet(() -> {
                    UUID fileId;
                    try {
                        fileId = binaryFileService
                                .storeFile(
                                        application(),
                                        file,
                                        "",
                                        Optional.ofNullable(fileOrUuid())
                                                .map(FileOrUUID::binaryfiledataset)
                                                .map(binaryFileDataset -> {
                                                    binaryFileDataset.setDatatype(dataName());
                                                    return binaryFileDataset;
                                                })
                                                .orElse(null));
                    } catch (IOException e) {
                        throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
                    }
                    BinaryFile binaryFile = binaryFileRepository.tryFindByIdWithData(fileId).orElse(null);
                    if (binaryFile == null) {
                        return null;
                    }
                    if (fileOrUuid() != null) {
                        binaryFile.withBinaryFileDataset(fileOrUuid().binaryfiledataset());
                    }
                    Preconditions.checkState(file != null);
                    binaryFileRepository.storeFileContent(fileId, inputStream, (int) file.fileSize().intValue());
                    return binaryFileRepository.tryFindByIdWithData(fileId).orElse(null);
                });
        if (builder().fileMustBeJustStored()) {
            return new JustStoredFile(builder());
        }
        if(fileOrUuid()!=null) {
            Optional.ofNullable(binaryFile())
                    .map(BinaryFile::getParams)
                    .map(fileOrUuid()::withParams)
                    .ifPresent(fileOrUUID -> builder().fileOrUUID = fileOrUUID);
        }
        return new UnPublishedVersions(builder());
    }

    public StoreFile testRights() {
        boolean isNewFileOrUUID = Optional.ofNullable(fileOrUuid()).map(FileOrUUID::fileid).isEmpty();
        boolean publishing = Optional.ofNullable(fileOrUuid()).map(FileOrUUID::topublish).orElse(false) ||
                             isNewFileOrUUID && !builder().isRepository();
        publishing = !application().isData(dataName()) || publishing;
        if (isNewFileOrUUID && !builder().applicationDataWriter().hasRightForDeposit(fileOrUuid())) {
            throw new NotApplicationDataWriterForDepositException(application().getName(), dataName());
        } else {
            if (publishing && !builder().applicationDataWriter().hasRightForPublishOrUnPublish(fileOrUuid())) {
                throw new NotApplicationDataWriterForPublishException(application().getName(), dataName());
            }
        }
        return this;
    }
}