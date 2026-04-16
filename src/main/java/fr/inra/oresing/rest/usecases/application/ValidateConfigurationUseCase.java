package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.services.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ValidateConfigurationUseCase {

    private final ApplicationService applicationService;

    public Application execute(Consumer<ReactiveResult> consumer, DataFile dataFile) {
        return applicationService.validateConfiguration(consumer, dataFile);
    }
}
