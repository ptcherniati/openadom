package fr.inra.oresing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.0")
                .withDatabaseName("test")
                .withUsername("postgres")
                .withPassword("postgres")
                .withInitScript("migration/openadom_user.sql");

        postgres.start();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername("openAdomTechUser");
        dataSource.setPassword("z2I<i}qclq)D?xqT");

        return dataSource;
    }
}