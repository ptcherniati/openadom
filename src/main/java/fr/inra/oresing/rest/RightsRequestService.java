package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.RightsRequestRepository;
import fr.inra.oresing.persistence.RightsRequestSearchHelper;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
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
public class RightsRequestService {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OreSiRepository repo;

    void addRightsRequest(final Application app, final String refType, final MultipartFile file, final UUID fileId) {
        RightsRequestRepository rightsRequestRepository = repo.getRepository(app).rightsRequestRepository();
    }

    /**
     *
     */
    //TODO use params
    List<RightsRequest> findRightsRequests(final Application application, final RightsRequestInfos rightsRequestInfos) {
        RightsRequestSearchHelper rightsRequestSearchHelper = new RightsRequestSearchHelper(application, rightsRequestInfos);
        String where = rightsRequestSearchHelper.buildWhereRequest();
        authenticationService.setRoleForClient();
        final List<RightsRequest> list = repo
                .getRepository(application)
                .rightsRequestRepository().findByCriteria(rightsRequestSearchHelper);
        return list;
    }

    private Application getApplication(final String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}