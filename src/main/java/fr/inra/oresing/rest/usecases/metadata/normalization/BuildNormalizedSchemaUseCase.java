package fr.inra.oresing.rest.usecases.metadata.normalization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.rest.services.NormalizedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static fr.inra.oresing.rest.services.NormalizedService.CANT_CREATE_DENORMALIZED_TABLE;

@Slf4j
@Component
public class BuildNormalizedSchemaUseCase {
    private final ApplicationService applicationService;
    private final NormalizedService normalizedService;
    private final ExecutorService executorService;

    public BuildNormalizedSchemaUseCase(
            ApplicationService applicationService,
            NormalizedService normalizedService,
            ExecutorService executorService) {
        this.applicationService = applicationService;
        this.normalizedService = normalizedService;
        this.executorService = executorService;
    }

    public String execute(String nameOrId) throws ExecutionException, InterruptedException {
        Application application = applicationService.getApplication(nameOrId);
        SecurityContext context = SecurityContextHolder.getContext();
        return executorService
                .submit(() -> {
                    SecurityContextHolder.setContext(context);
                    try {
                        return normalizedService.buildNormalizedSchema(application, true);
                    } catch (Exception e) {
                        log.error("Error building normalized schema for application {}", nameOrId, e);
                        return CANT_CREATE_DENORMALIZED_TABLE;
                    }
                })
                .get();
    }
}
