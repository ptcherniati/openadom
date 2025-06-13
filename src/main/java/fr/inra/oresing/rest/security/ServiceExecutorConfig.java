package fr.inra.oresing.rest.security;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync // Ajouter cette annotation
public class ServiceExecutorConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        // Utiliser un exécuteur de threads virtuels
        return new DelegatingSecurityContextAsyncTaskExecutor(
                new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor())
        );
    }
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return handler -> handler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

}