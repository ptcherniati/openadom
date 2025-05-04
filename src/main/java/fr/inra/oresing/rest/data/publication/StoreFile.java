package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForPublishException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.rest.exceptions.OreSiIOException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

public record StoreFile(AuthorizationPublicationService builder) implements State {

    public State loadOrCreateFile(
            MultipartFile file,
            BinaryFileRepository binaryFileRepository,
            BinaryFileService binaryFileService
    ) {

        try {
            InputStream inputStream = file==null?null:file.getInputStream();
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
                                            Optional.ofNullable(fileOrUuid()).map(FileOrUUID::binaryfiledataset).orElse(null));
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
                        assert file != null;
                        binaryFileRepository.storeFileContent(fileId, inputStream, (int) file.getSize());
                        return binaryFileRepository.tryFindByIdWithData(fileId).orElse(null);
                    });
            if (builder().fileMustBeJustStored()) {
                return new JustStoredFile(builder());
            }
            return new UnPublishedVersions(builder());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
    }

    public StoreFile testRights() {
        boolean newFileOrUUID = Optional.ofNullable(fileOrUuid()).map(FileOrUUID::fileid).isEmpty();
        boolean publishing = Optional.ofNullable(fileOrUuid()).map(FileOrUUID::topublish).orElse(false) ||
                             newFileOrUUID && !builder().isRepository();
        publishing = !application().isData(dataName()) || publishing;
        if (newFileOrUUID && !builder().applicationDataWriter().hasRightForDeposit(fileOrUuid())) {
            throw new NotApplicationDataWriterForDepositException(application().getName(), dataName());
        } else {
            if (publishing && builder().applicationDataWriter().hasRightForPublishOrUnPublish(fileOrUuid())) {
                throw new NotApplicationDataWriterForPublishException(application().getName(), dataName());
            }
        }
        return this;
    }
}