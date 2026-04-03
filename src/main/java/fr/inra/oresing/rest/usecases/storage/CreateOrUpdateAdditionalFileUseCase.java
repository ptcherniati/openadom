package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
public class CreateOrUpdateAdditionalFileUseCase {

    private final AdditionalFileService additionalFileService;

    public CreateOrUpdateAdditionalFileUseCase(AdditionalFileService additionalFileService) {
        this.additionalFileService = additionalFileService;
    }

    @Transactional
    public UUID execute(CreateAdditionalFileRequest request, String additionalFileName, String nameOrId, MultipartFile file) {
        return additionalFileService.createOrUpdate(request, additionalFileName, nameOrId, file);
    }
}
