package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.rightsrequest.GetAdditionalFilesResult;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.springframework.stereotype.Component;

@Component
public class FindAdditionalFileUseCase {
    private final AdditionalFileService additionalFileService;

    public FindAdditionalFileUseCase(AdditionalFileService additionalFileService) {
        this.additionalFileService = additionalFileService;
    }

    public GetAdditionalFilesResult execute(String nameOrId, AdditionalFilesInfos additionalFilesInfos) {
        return additionalFileService.findAdditionalFile(nameOrId, additionalFilesInfos);
    }
}
