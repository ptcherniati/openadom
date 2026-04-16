package fr.inra.oresing.domain.application.configuration.migration.context;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.data.migration.MigrationConfiguration;
import fr.inra.oresing.rest.services.ServiceContainer;

public record MigrationContext(
        MigrationConfiguration.MigrationRepositories migrationRepositories,
        ServiceContainer serviceContainer,
        String applicationName,
        Application oldApplication,
        Application newApplication,
        SchemaInfo schemaInfo,
        DataInfo dataInfo
) {
    public OreSiRepository.RepositoryForApplication getRepositoryForApplication(){
        return migrationRepositories().repository().getRepository(applicationName());
    }
}