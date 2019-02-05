package fr.inra.oresing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"fr.inra.oresing"})
public class OreSiNg {

    public static void main(String[] args) {
        SpringApplication.run(OreSiNg.class, args);
    }

    /**
     * Mapper json pour la persistence (dialogue avec la base de données)
     */
    @Bean
    public ObjectMapper sqlJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // there is no case in SQL, but in java we love camelCase :p
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE)
        ;
        return mapper;
    }

}

