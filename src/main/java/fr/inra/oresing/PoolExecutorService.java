package fr.inra.oresing;


import lombok.extern.java.Log;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Stream;

@Service
@EnableScheduling
@EnableAsync
@Configuration
@Log
public class PoolExecutorService implements AsyncConfigurer {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    @Override
    public ExecutorService getAsyncExecutor() {
        return POOL;
    }

    public <T> void runInPool(final Collection<T> from, final Consumer<T> methods) {
        Objects.requireNonNull(getAsyncExecutor()).execute(() -> from.parallelStream().forEach(methods));
    }

    public <T> void runInPool(final Stream<T> from, final Consumer<T> methods) {
        POOL.execute(() -> from.parallel().forEach(methods));
    }

    public <T, A, R> R runInPool(final Collection<T> from, final Collector<? super T, A, R> collector) throws ExecutionException, InterruptedException {
        try {
            final Future<R> submit = Objects.requireNonNull(getAsyncExecutor()).submit(() -> from.parallelStream().collect(collector));
            return submit.get();
        } catch (final ExecutionException | InterruptedException e) {
            log.severe(e.getLocalizedMessage());
            throw e;
        }
    }

    public <T, A, R> R runInPool(final Stream<T> from, final Collector<? super T, A, R> collector) throws ExecutionException, InterruptedException {
        try {
            final Future<R> submit = POOL.submit(() -> from.parallel().collect(collector));
            return submit.get();
        } catch (final ExecutionException | InterruptedException e) {
            log.severe(e.getLocalizedMessage());
            throw e;
        }
    }
}
