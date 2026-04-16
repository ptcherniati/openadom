package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public record ConfigurationBuiderTestBuilder<T>(T result, List<ValidationError> errors) {
    public static ConfigurationBuiderTestBuilder<?> of(InputStream config, Function<Configuration, ?> doWithconfiguration) {
        return executeDoWithConfigurationTest(config, doWithconfiguration);
    }

    private static <T> ConfigurationBuiderTestBuilder<T> executeDoWithConfigurationTest(InputStream config, Function<Configuration, T> doWithconfiguration) {
        List<T> results = new LinkedList<>();
        List<ValidationError> errors = Flux.<ReactiveResult>create(fluxSink -> {
                    ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");
                    Configuration configuration = ConfigurationBuilder.build(config, eventHelper, "une application de test");
                    // Call the function you want to test
                    results.add(doWithconfiguration.apply(configuration));
                    fluxSink.complete();
                })
                .flatMap(reactiveResult -> switch (reactiveResult) {
                    case final ReactiveTypeError re -> Mono.just(re);
                    default -> Mono.empty();
                })
                .map(ReactiveTypeError::result)
                .map(ValidationError.class::cast)
                .collectList()
                .block();
        return new ConfigurationBuiderTestBuilder<>(results.getFirst(), errors);
    }
}