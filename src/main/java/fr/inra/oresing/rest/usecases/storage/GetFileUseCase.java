package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GetFileUseCase {
    private final BinaryFileService binaryFileService;

    public GetFileUseCase(BinaryFileService binaryFileService) {
        this.binaryFileService = binaryFileService;
    }

    public Optional<BinaryFile> execute(String applicationName, UUID fileId) {
        return binaryFileService.getFile(applicationName, fileId);
    }
}
