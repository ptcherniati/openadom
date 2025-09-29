package fr.inra.oresing.rest.denormalization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.services.DenormalizedService;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class DenormalizationResources {
    final ServiceContainer serviceContainer;
    private final JsonRowMapper mapper;
    private final DenormalizedService denormalizedService;

    public DenormalizationResources(
            ServiceContainer serviceContainer,
            JsonRowMapper mapper,
            DenormalizedService denormalizedService
    ) {
        this.serviceContainer = serviceContainer;
        this.mapper = mapper;
        this.denormalizedService = denormalizedService;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @PostMapping(value = "/applications/{nameOrId}/denormalized")
    public void buildDenormalizedSchema(@PathVariable("nameOrId") final String nameOrId) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        denormalizedService.buildDenormalizedSchema(application);
    }


}