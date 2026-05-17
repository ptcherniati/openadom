package fr.inra.oresing.config;

import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.services.ServiceContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {

    @Bean
    @SuppressWarnings("java:S1452")
    public JsonRowMapper<?> jsonRowMapper(ServiceContainer serviceContainer) {
        return new JsonRowMapper<>(serviceContainer);
    }
}