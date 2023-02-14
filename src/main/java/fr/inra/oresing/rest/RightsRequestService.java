package fr.inra.oresing.rest;

import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.rightsrequest.RightsRequest;
import fr.inra.oresing.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.RightsRequestRepository;
import fr.inra.oresing.persistence.RightsRequestSearchHelper;
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
public class RightsRequestService {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private OreSiRepository repo;

    void addRightsRequest(Application app, String refType, MultipartFile file, UUID fileId) throws IOException {
        final RightsRequestRepository rightsRequestRepository = repo.getRepository(app).rightsRequestRepository();
    }

    /**
     * @param nameOrId l'id de l'application
     * @param params   les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    //TODO use params
    List<RightsRequest> findRightsRequests(Application application, RightsRequestInfos rightsRequestInfos) {
        final RightsRequestSearchHelper rightsRequestSearchHelper = new RightsRequestSearchHelper(application, rightsRequestInfos);
        final String where = rightsRequestSearchHelper.buildWhereRequest();
        authenticationService.setRoleForClient();
        List<RightsRequest> list = repo.getRepository(application).rightsRequestRepository().findAllByWhereClause(where, rightsRequestSearchHelper.getParamSource());
        return list;
    }

    private Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}