package fr.inra.oresing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

@Slf4j
public class PostgresContainerTestExecutionListener implements TestExecutionListener {

    private final PostgreSQLContainer postgreSQLContainer;

    {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres:10.14")
                .withDatabaseName("ore-si")
                .withUsername("dbuser")
                .withPassword("xxxxxxxx");
        postgreSQLContainer.setPortBindings(List.of(PostgreSQLContainer.POSTGRESQL_PORT + ":" + PostgreSQLContainer.POSTGRESQL_PORT));
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        if (log.isTraceEnabled()) {
            log.trace("démarre un conteneur postgresql disponible à l'adresse " + postgreSQLContainer.getJdbcUrl());
        }
        postgreSQLContainer.start();
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        postgreSQLContainer.stop();
    }
}
