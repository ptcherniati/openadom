package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.services.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetApplicationsUseCase {

    private final ApplicationService applicationService;

    public Flux<ReactiveResult> execute(List<ApplicationInformation> filters) {
        return applicationService.getApplications(filters);
    }
}
