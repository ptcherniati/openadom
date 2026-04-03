package fr.inra.oresing.rest.usecases.metadata;

import fr.inra.oresing.rest.model.rightsrequest.CreateRightsRequestRequest;
import fr.inra.oresing.rest.services.RightsRequestService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class CreateOrUpdateRightsRequestUseCase {
    private final RightsRequestService rightsRequestService;

    public CreateOrUpdateRightsRequestUseCase(RightsRequestService rightsRequestService) {
        this.rightsRequestService = rightsRequestService;
    }

    @Transactional
    public UUID execute(CreateRightsRequestRequest request, String nameOrId) {
        return rightsRequestService.createOrUpdate(request, nameOrId);
    }
}
