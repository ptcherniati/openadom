package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.persistence.AdditionalFileRepository;
import fr.inra.oresing.persistence.AdditionalFileSearchHelper;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Transactional(readOnly = true)
public class AdditionalFileService {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OreSiRepository repo;

    @Transactional
    void addAdditionalfile(final Application application, final String refType, final MultipartFile file, final UUID fileId) {
        AdditionalFileRepository additionalFileRepository = repo.getRepository(application).additionalBinaryFile();
    }


    @Transactional(readOnly = true)
    AdditionalBinaryFile findCharte(final Application application) {
        return repo.getRepository(application).additionalBinaryFile()
                .findById(application.getId());
    }

    /**
     *
     */
    List<AdditionalBinaryFile> findAdditionalFile(final Application application, final AdditionalFilesInfos additionalFilesInfos) {
        AdditionalFileSearchHelper additionalFileSearchHelper = new AdditionalFileSearchHelper(application, additionalFilesInfos);
        String where = additionalFileSearchHelper.buildWhereRequest();
        authenticationService.setRoleForClient();
        return repo
                .getRepository(application)
                .additionalBinaryFile()
                .findByCriteria(additionalFileSearchHelper);
    }

    private Application getApplication(final String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}