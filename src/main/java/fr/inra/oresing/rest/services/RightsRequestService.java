package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.RightsRequestRepository;
import fr.inra.oresing.persistence.RightsRequestSearchHelper;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import fr.inra.oresing.rest.model.rightsrequest.CreateRightsRequestRequest;
import fr.inra.oresing.rest.model.rightsrequest.GetRightsRequestResult;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestResult;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Component
@Transactional(readOnly = true)
public class RightsRequestService implements ServiceContainerBean {

    @Setter
    private ServiceContainer serviceContainer;

    @Autowired
    private OreSiRepository repository;
    @Autowired
    private OreSiApiRequestContext request;

    void addRightsRequest(final Application app, final String refType, final MultipartFile file, final UUID fileId) {
        RightsRequestRepository rightsRequestRepository = repository.getRepository(app).rightsRequestRepository();
    }

    /**
     *
     */
    //TODO use params
    List<RightsRequest> findRightsRequests(final Application application, final RightsRequestInfos rightsRequestInfos) {
        RightsRequestSearchHelper rightsRequestSearchHelper = new RightsRequestSearchHelper(application, rightsRequestInfos);
        String where = rightsRequestSearchHelper.buildWhereRequest();
        serviceContainer.authenticationService().setRoleForClient();
        return repository
                .getRepository(application)
                .rightsRequestRepository().findByCriteria(rightsRequestSearchHelper);
    }

    private Application getApplication(final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        return repository.application().findApplication(nameOrId);
    }

    public GetRightsRequestResult findRightsRequest(final String nameOrId, final RightsRequestInfos rightsRequestInfos) {
        Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
        final RightRequestDescription description = application.getConfiguration().rightsRequest();
        List<RightsRequest> rightsRequests = serviceContainer.rightsRequestService().findRightsRequests(application, rightsRequestInfos);
        List<RightsRequestResult> rightsRequestResult = rightsRequests.stream()
                .map(rightsRequest ->
                        getRightsRequestResult(rightsRequest, application)
                )
                .toList();
        ImmutableSortedSet<GetGrantableResult.User> grantableUsers = serviceContainer.authorizationService().getGrantableUsers();
        return new GetRightsRequestResult(grantableUsers, rightsRequestResult, description);
    }

    private RightsRequestResult getRightsRequestResult(final RightsRequest rightsRequest, final Application application) {
        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        AuthorizationService.authorizationsToParsedAuthorizations(
                List.of(rightsRequest.getRightsRequest()),
                authorizationsParsed);
        return new RightsRequestResult(
                rightsRequest,
                authorizationsParsed
        );
    }


    @Transactional()
    public UUID createOrUpdate(final CreateRightsRequestRequest createRightsRequestRequest, final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        final Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);

        RightsRequest rightsRequest = Optional.of(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::id)
                .map(id -> repository.getRepository(application).rightsRequestRepository().findById(id))
                .orElseGet(RightsRequest::new);
        rightsRequest.setRightsRequestForm(createRightsRequestRequest.fields());
        rightsRequest.setApplication(application.getId());
        rightsRequest.setComment(createRightsRequestRequest.comment());
        rightsRequest.setSetted(createRightsRequestRequest.setted());
        rightsRequest.setId(rightsRequest.getId() == null ? UUID.randomUUID() : rightsRequest.getId());
        OreSiAuthorization authorizations = Optional.of(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::rightsRequest)
                .map(authorization -> {
                    List errors = new ArrayList<>();
                    AuthorizationRequest authorizationRequestToAuthorizationRequest = serviceContainer.authorizationService().createAuthorizationRequestToAuthorizationRequest(
                            authorization,
                            application,
                            List.of(serviceContainer.authorizationService().getCurrentUser().getId()),
                            List.of(),
                            errors
                    );
                    OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
                    oreSiAuthorization.setId(rightsRequest.getId());
                    oreSiAuthorization.setApplication(application.getId());
                    oreSiAuthorization.setAuthorizations(authorizationRequestToAuthorizationRequest.buildAuthorizationsByDataname());
                    return oreSiAuthorization;
                })
                .orElse(null);
        rightsRequest.setRightsRequest(authorizations);
        rightsRequest.setUser(rightsRequest.getUser() == null ? request.getRequestUserId() : rightsRequest.getUser());
        rightsRequest.getRightsRequest().setOreSiUsers(Set.of(rightsRequest.getUser()));
        serviceContainer.authenticationService().setRoleForClient();
        return repository.getRepository(application).rightsRequestRepository().store(rightsRequest);
    }


}