package fr.inra.oresing.rest;

import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.persistence.AdditionalFileRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
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
     * @param nameOrId l'id de l'application
     * @param additionalFileName  le type de fichier additionel
     * @param params   les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    List<AdditionalBinaryFile> findAdditionalFile(String nameOrId, String additionalFileName, MultiValueMap<String, String> params) {
        authenticationService.setRoleForClient();
        List<AdditionalBinaryFile> list = repo.getRepository(nameOrId).additionalBinaryFile().findAllByFileType(additionalFileName);
        return list;
    }

    private Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}