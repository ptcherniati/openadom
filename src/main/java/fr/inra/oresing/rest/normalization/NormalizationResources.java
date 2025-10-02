package fr.inra.oresing.rest.normalization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.rest.services.NormalizedService;
import fr.inra.oresing.rest.services.ServiceContainer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class NormalizationResources {
    final ServiceContainer serviceContainer;
    private final JsonRowMapper mapper;
    private final NormalizedService normalizedService;
    private final ExecutorService executorService;
    private final SqlService sqlService;

    public NormalizationResources(
            ServiceContainer serviceContainer,
            JsonRowMapper mapper,
            NormalizedService normalizedService,
            ExecutorService executorService, SqlService sqlService) {
        this.serviceContainer = serviceContainer;
        this.mapper = mapper;
        this.normalizedService = normalizedService;
        this.executorService = executorService;
        this.sqlService = sqlService;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @PostMapping(value = "/applications/{nameOrId}/normalized",produces = {MediaType.TEXT_PLAIN_VALUE})
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<String> buildNormalizedSchema(@PathVariable("nameOrId") final String nameOrId) throws ExecutionException, InterruptedException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final SecurityContext context = SecurityContextHolder.getContext();
        final String sql = executorService
                .submit(() -> {
                    SecurityContextHolder.setContext(context);
                    return normalizedService.buildNormalizedSchema(application, true);
                })
                .get();
        return ResponseEntity.ok(sql);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @GetMapping(value = "/applications/{nameOrId}/normalized",produces = {MediaType.TEXT_PLAIN_VALUE})
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<String> getNormalizedSchema(@PathVariable("nameOrId") final String nameOrId) throws ExecutionException, InterruptedException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final SecurityContext context = SecurityContextHolder.getContext();
        final String sql = executorService
                .submit(() -> {
                    SecurityContextHolder.setContext(context);
                    return normalizedService.buildNormalizedSchema(application, false);
                })
                .get();
        return ResponseEntity.ok(sql);
    }


}