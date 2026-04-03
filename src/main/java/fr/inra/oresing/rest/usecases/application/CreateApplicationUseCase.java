package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class CreateApplicationUseCase {

    private final ApplicationService applicationService;

    public void execute(Consumer<ReactiveResult> consumer, String name, DataFile dataFile, String comment) throws IOException {
        applicationService.createApplication(consumer, name, dataFile, comment);
    }
}
