package fr.inra.oresing.rest;

import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.model.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.persistence.AdditionalFileRepository;
import fr.inra.oresing.persistence.AdditionalFileSearchHelper;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Transactional
public class AdditionalFileService {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private OreSiRepository repo;

    void addAdditionalfile(Application app, String refType, MultipartFile file, UUID fileId) throws IOException {
        final AdditionalFileRepository additionalFileRepository = repo.getRepository(app).additionalBinaryFile();
    }

    /**
     *
     * @param application
     * @param additionalFilesInfos
     * @return
     */
    List<AdditionalBinaryFile> findAdditionalFile(Application application, AdditionalFilesInfos additionalFilesInfos) {
        final AdditionalFileSearchHelper additionalFileSearchHelper = new AdditionalFileSearchHelper(application, additionalFilesInfos);
        final String where = additionalFileSearchHelper.buildWhereRequest();
        authenticationService.setRoleForClient();
        return repo
                .getRepository(application)
                .additionalBinaryFile()
                .findByCriteria(additionalFileSearchHelper);
    }

    private Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}