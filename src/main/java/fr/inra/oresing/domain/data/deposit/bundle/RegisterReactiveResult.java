package fr.inra.oresing.domain.data.deposit.bundle;

import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeProgress;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.atomic.AtomicInteger;

public record RegisterReactiveResult(
        FluxSink<ReactiveResult<?>> resultFluxSink,
        BundleReport bundleReport,
        int countFiles,
        AtomicInteger done

) {
    public RegisterReactiveResult(FluxSink<ReactiveResult<?>> resultFluxSink, int countFiles,  BundleReport bundleReport) {
        this(resultFluxSink, bundleReport, countFiles, new AtomicInteger(0));
    }

    public void add(ReactiveResult<?> reactiveResult, boolean increment) {
        try {
            resultFluxSink().next(reactiveResult);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to emit reactive result: " + e.getMessage(), e);
        } finally {
            bundleReport().add(reactiveResult);
            if(increment){
                int progress = done.incrementAndGet();
                resultFluxSink().next(new ReactiveTypeProgress<>(0.1 + (0.9 / countFiles) * progress));
            }

        }
    }
}