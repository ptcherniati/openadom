package fr.inra.oresing.rest.usecases.metadata.normalization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.rest.services.NormalizedService;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Component
public class GetNormalizedSchemaUseCase {
    private final ApplicationService applicationService;
    private final NormalizedService normalizedService;
    private final ExecutorService executorService;

    public GetNormalizedSchemaUseCase(
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
        try {
            return executorService
                    .submit(() -> {
                        SecurityContextHolder.setContext(context);
                        try {
                            return normalizedService.buildNormalizedSchema(application, false);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    })
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
