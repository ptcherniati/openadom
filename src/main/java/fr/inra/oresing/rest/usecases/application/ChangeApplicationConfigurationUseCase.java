package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ChangeApplicationConfigurationUseCase {

    private final ApplicationService applicationService;

    public UUID execute(Consumer<ReactiveResult> consumer, String nameOrId, DataFile dataFile, String comment) {
        return applicationService.changeApplicationConfiguration(consumer, nameOrId, dataFile, comment);
    }
}
