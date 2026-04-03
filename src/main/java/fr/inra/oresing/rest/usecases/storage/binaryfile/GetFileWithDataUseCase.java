package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetFileWithDataUseCase {

    private final BinaryFileService binaryFileService;

    public GetFileWithDataUseCase(BinaryFileService binaryFileService) {
        this.binaryFileService = binaryFileService;
    }

    public Optional<BinaryFile> execute(String applicationNameOrId, UUID fileId) {
        return binaryFileService.getFileWithData(applicationNameOrId, fileId);
    }
}
