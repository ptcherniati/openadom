package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.services.AdditionalFileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;

@Component
public class GetCharteUseCase {

    private final AdditionalFileService additionalFileService;

    public GetCharteUseCase(AdditionalFileService additionalFileService) {
        this.additionalFileService = additionalFileService;
    }

    public void execute(OutputStream out, HttpServletResponse response, String nameOrId, AdditionalFilesInfos additionalFilesInfos) 
            throws BadAdditionalFileParamsSearchException, IOException {
        additionalFileService.getCharte(out, response, nameOrId, additionalFilesInfos);
    }
}
