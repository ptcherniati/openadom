package fr.inra.oresing.executor;


import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

@Configuration
@EnableAsync
public class AsyncExecutorConfiguration implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutorConfiguration.class);

    @Autowired
    private ExecutorProperties properties;

    public static class ContextPropagatingTaskDecorator implements TaskDecorator {

        @NonNull
        @Override
        public Runnable decorate(@NonNull Runnable runnable) {
            SecurityContext securityContext = SecurityContextHolder.getContext();
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    if (securityContext != null) {
                        SecurityContextHolder.setContext(securityContext);
                    }
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    }
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }

                    runnable.run();

                } finally {
                    MDC.clear();
                    RequestContextHolder.resetRequestAttributes();
                    SecurityContextHolder.clearContext();
                }
            };
        }
    }

    /// Creates the Fast Service Executor with properties from configuration.
    @Bean(name = "fastServiceExecutor")
    public Executor fastServiceExecutor() {
        ExecutorProperties.Fast config = properties.getFast();

        log.info("Configuring Fast Executor - Core: {}, Max: {}, Queue: {}",
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());

        if (properties.isUseVirtualThreads()) {
            executor.setThreadFactory(Thread.ofVirtual()
                    .name(config.getThreadNamePrefix(), 0)
                    .uncaughtExceptionHandler((t, e) ->
                            log.error("Uncaught exception in virtual thread [{}]: {}", t.getName(), e.getMessage(), e))
                    .factory());
        }

        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(properties.isAwaitTermination());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());

        executor.initialize();
        return executor;
    }

    /// Creates the Normal Service Executor with properties from configuration.
    @Bean(name = "normalServiceExecutor")
    public Executor normalServiceExecutor() {
        ExecutorProperties.Normal config = properties.getNormal();

        log.info("Configuring Normal Executor - Core: {}, Max: {}, Queue: {}",
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());

        if (properties.isUseVirtualThreads()) {
            executor.setThreadFactory(Thread.ofVirtual()
                    .name(config.getThreadNamePrefix(), 0)
                    .uncaughtExceptionHandler((t, e) ->
                            log.error("Uncaught exception in virtual thread [{}]: {}", t.getName(), e.getMessage(), e))
                    .factory());
        }

        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(properties.isAwaitTermination());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());

        executor.initialize();
        return executor;
    }

    /// Creates the Heavy Service Executor with properties from configuration.
    @Bean(name = "heavyServiceExecutor")
    public Executor heavyServiceExecutor() {
        ExecutorProperties.Heavy config = properties.getHeavy();

        log.info("Configuring Heavy Executor - Core: {}, Max: {}, Queue: {}",
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());

        if (properties.isUseVirtualThreads()) {
            executor.setThreadFactory(Thread.ofVirtual()
                    .name(config.getThreadNamePrefix(), 0)
                    .uncaughtExceptionHandler((t, e) ->
                            log.error("Uncaught exception in virtual thread [{}]: {}", t.getName(), e.getMessage(), e))
                    .factory());
        }

        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler(new CustomRejectionHandler());

        executor.setWaitForTasksToCompleteOnShutdown(properties.isAwaitTermination());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());

        executor.initialize();
        return executor;
    }

    /// Creates the Backup Executor with properties from configuration.
    @Bean(name = "backupExecutor")
    public Executor backupExecutor() {
        ExecutorProperties.Backup config = properties.getBackup();

        log.info("Configuring Backup Executor - Core: {}, Max: {}, Queue: {}",
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());

        if (properties.isUseVirtualThreads()) {
            executor.setThreadFactory(Thread.ofVirtual()
                    .name(config.getThreadNamePrefix(), 0)
                    .uncaughtExceptionHandler((t, e) ->
                            log.error("Uncaught exception in virtual thread [{}]: {}", t.getName(), e.getMessage(), e))
                    .factory());
        }

        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());

        executor.setRejectedExecutionHandler((r, ex) -> {
            log.warn("Backup rejected - system overloaded");
            throw new RejectedExecutionException(
                    "Too many backups in progress, retry later"
            );
        });

        executor.setWaitForTasksToCompleteOnShutdown(properties.isAwaitTermination());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());

        executor.initialize();
        return executor;
    }
    @Bean(name = "fastExecutorService")
    public ExecutorService fastExecutorService() {
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) fastServiceExecutor();
        return taskExecutor.getThreadPoolExecutor();
    }

    /**
     * ExecutorService for manual Future management (normal operations).
     *
     * @return ExecutorService wrapping the normal executor
     */
    @Bean(name = "normalExecutorService")
    public ExecutorService normalExecutorService() {
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) normalServiceExecutor();
        return taskExecutor.getThreadPoolExecutor();
    }

    /**
     * ExecutorService for manual Future management (heavy operations).
     *
     * @return ExecutorService wrapping the heavy executor
     */
    @Bean(name = "heavyExecutorService")
    public ExecutorService heavyExecutorService() {
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) heavyServiceExecutor();
        return taskExecutor.getThreadPoolExecutor();
    }

    /**
     * ExecutorService for manual Future management (backup operations).
     *
     * @return ExecutorService wrapping the backup executor
     */
    @Bean(name = "backupExecutorService")
    public ExecutorService backupExecutorService() {
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) backupExecutor();
        return taskExecutor.getThreadPoolExecutor();
    }

    /**
     * Default ExecutorService for backward compatibility.
     * Points to normalExecutorService.
     *
     * @deprecated Use specific executors (fast/normal/heavy/backup) instead
     */
    @Bean(name = "executorService")
    @Deprecated
    public ExecutorService executorService() {
        return normalExecutorService();
    }

    @Override
    public Executor getAsyncExecutor() {
        return normalServiceExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Uncaught exception in @Async: method={}, params={}",
                    method.getName(),
                    Arrays.toString(params),
                    ex);

            Metrics.counter("async.errors",
                    "method", method.getName(),
                    "exception", ex.getClass().getSimpleName()
            ).increment();
        }
    }

    public static class CustomRejectionHandler implements RejectedExecutionHandler {

        private static final Logger log = LoggerFactory.getLogger(CustomRejectionHandler.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Task rejected - Pool: {}/{}, Queue: {}/{}",
                    executor.getActiveCount(),
                    executor.getPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity()
            );

            Metrics.counter("executor.rejections").increment();

            throw new RejectedExecutionException("Service overloaded");
        }
    }
}