package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.zip.ZipOutputStream;

@Component
public class GetAdditionalFilesZipStreamUseCase {

    private final AdditionalFileService additionalFileService;

    public GetAdditionalFilesZipStreamUseCase(AdditionalFileService additionalFileService) {
        this.additionalFileService = additionalFileService;
    }

    public void execute(ZipOutputStream zipOutputStream, String nameOrId, AdditionalFilesInfos additionalFilesInfos) 
            throws BadAdditionalFileParamsSearchException, IOException {
        additionalFileService.getAdditionalFilesNamesZipStream(zipOutputStream, nameOrId, additionalFilesInfos);
    }
}
