package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public record StoreFile(AuthorizationPublicationService builder) implements State {

    public State loadOrCreateFile(
            MultipartFile file,
            BinaryFileRepository binaryFileRepository,
            BinaryFileService binaryFileService
    ) throws IOException {
            byte[] bytes = file==null?null : FileBomResolver.of(file.getInputStream()).readAllBytes();
            assert builder().hasRightForDeposit();

            BinaryFile storedFile = Optional.ofNullable(params()).map(FileOrUUID::fileid)
                    .flatMap(uuid -> binaryFileRepository.tryFindByIdWithData(uuid))
                    .orElseGet(() -> {
                        UUID fileId = null;
                        try {
                            fileId = binaryFileService
                                    .storeFile(
                                            application(),
                                            file,
                                            "",
                                            Optional.ofNullable(params()).map(p -> p.binaryfiledataset()).orElse(null));
                        } catch (IOException e) {
                            throw null;
                        }
                        BinaryFile binaryFile = binaryFileRepository.tryFindByIdWithData(fileId).orElse(null);
                        if (binaryFile == null) {
                            return null;
                        }
                        if (params() != null) {
                            binaryFile.withBinaryFileDataset(params().binaryfiledataset());
                        }
                        binaryFile.setFileData(bytes);
                        fileId = binaryFileRepository.store(binaryFile);
                        return binaryFile;
                    });
            builder().binaryFile = storedFile;
        ;
            if(builder().fileMustBeJustStored()){
                return new JustStoredFile(builder());
            }
        return new UnPublishedVersions(builder());
    }
}
