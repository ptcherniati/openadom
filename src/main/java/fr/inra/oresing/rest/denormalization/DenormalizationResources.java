package fr.inra.oresing.rest.denormalization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.rest.services.DenormalizedService;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class DenormalizationResources {
    final ServiceContainer serviceContainer;
    private final JsonRowMapper mapper;
    private final DenormalizedService denormalizedService;
    private final ExecutorService executorService;
    private final SqlService sqlService;

    public DenormalizationResources(
            ServiceContainer serviceContainer,
            JsonRowMapper mapper,
            DenormalizedService denormalizedService,
            ExecutorService executorService, SqlService sqlService) {
        this.serviceContainer = serviceContainer;
        this.mapper = mapper;
        this.denormalizedService = denormalizedService;
        this.executorService = executorService;
        this.sqlService = sqlService;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @PostMapping(value = "/applications/{nameOrId}/denormalized")
    public ResponseEntity<String> buildDenormalizedSchema(@PathVariable("nameOrId") final String nameOrId) throws ExecutionException, InterruptedException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final String sql = executorService
                .submit(() -> denormalizedService.buildDenormalizedSchema(application))
                .get();
        return ResponseEntity.ok(sql);
    }


}