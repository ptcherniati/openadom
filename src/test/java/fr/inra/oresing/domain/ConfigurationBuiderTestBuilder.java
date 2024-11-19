package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public record ConfigurationBuiderTestBuilder<T>(T result, List<ValidationError> errors) {
    public static ConfigurationBuiderTestBuilder<?> of(String config, Function<Configuration, ?> doWithconfiguration) {
        return executeDoWithConfigurationTest(config, doWithconfiguration);
    }

    private static <T> ConfigurationBuiderTestBuilder<T> executeDoWithConfigurationTest(String config, Function<Configuration, T> doWithconfiguration) {
        List<T> results = new LinkedList<>();
        List<ValidationError> errors = Flux.<ReactiveResult>create(fluxSink -> {
                    final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
                    Configuration configuration = ConfigurationBuilder.build(config.getBytes(), progression, "une application de test");
                    // Call the function you want to test
                    results.add(doWithconfiguration.apply(configuration));
                    fluxSink.complete();
                })
                .flatMap(reactiveResult -> {
                    return switch (reactiveResult) {
                        case final ReactiveTypeError re -> Mono.just(re);
                        default -> Mono.empty();
                    };
                })
                .map(ReactiveTypeError::result)
                .map(ValidationError.class::cast)
                .collectList()
                .block();
        return new ConfigurationBuiderTestBuilder<>(results==null?null: results.get(0), errors);
    }
}
