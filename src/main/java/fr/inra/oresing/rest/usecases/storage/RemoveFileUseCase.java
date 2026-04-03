package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class RemoveFileUseCase {

    private final BinaryFileService binaryFileService;

    public RemoveFileUseCase(BinaryFileService binaryFileService) {
        this.binaryFileService = binaryFileService;
    }

    @Transactional
    public Optional<UUID> execute(Application application, UUID fileId) {
        return binaryFileService.removeFile(application, fileId);
    }
}
