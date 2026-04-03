package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class DeleteAdditionalFilesUseCase {

    private final AdditionalFileService additionalFileService;

    public DeleteAdditionalFilesUseCase(AdditionalFileService additionalFileService) {
        this.additionalFileService = additionalFileService;
    }

    @Transactional
    public List<UUID> execute(String nameOrId, AdditionalFilesInfos additionalFilesInfos) throws BadAdditionalFileParamsSearchException {
        return additionalFileService.deleteAdditionalFiles(nameOrId, additionalFilesInfos);
    }
}
