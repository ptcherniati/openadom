package fr.inra.oresing.rest.usecases.metadata;

import fr.inra.oresing.rest.model.rightsrequest.GetRightsRequestResult;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.rest.services.RightsRequestService;
import org.springframework.stereotype.Component;

@Component
public class FindRightsRequestUseCase {
    private final RightsRequestService rightsRequestService;

    public FindRightsRequestUseCase(RightsRequestService rightsRequestService) {
        this.rightsRequestService = rightsRequestService;
    }

    public GetRightsRequestResult execute(String nameOrId, RightsRequestInfos rightsRequestInfos) {
        return rightsRequestService.findRightsRequest(nameOrId, rightsRequestInfos);
    }
}
