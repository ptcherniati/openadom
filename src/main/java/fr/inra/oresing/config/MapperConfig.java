package fr.inra.oresing.config;

import fr.inra.oresing.persistence.JsonRowMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {

    @Bean
    @SuppressWarnings("java:S1452")
    public JsonRowMapper<?> jsonRowMapper() {
        return new JsonRowMapper<>();
    }
}