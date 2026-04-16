package fr.inra.oresing.rest.data.migration;

import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.application.configuration.migration.execution.MigrationExecutor;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.metamodel.clazz.ValueObjectDefinitionBuilder;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;

@Configuration
public class MigrationConfiguration {
    public record MigrationRepositories(OreSiRepository repository){

    }

    @Bean
    public MigrationRepositories migrationRepositories(OreSiRepository repository) {
        return new MigrationRepositories(repository);
    }
    @Bean
    public MigrationExecutor executor(){
        return new MigrationExecutor();
    }

    @Bean
    public RulesEngine ruleEngine(){
        return new DefaultRulesEngine();
    }

    @Bean
    public Rules rules(){
        return new Rules();
    }

    @Bean
    public Javers javers(){
        return JaversBuilder.javers()
                        .withInitialChanges(false)
                        .registerValue(LocalDateTime.class)
                        .registerValue(TemporalAccessor.class)
                        .registerValueObject(
                                ValueObjectDefinitionBuilder
                                        .valueObjectDefinition(fr.inra.oresing.domain.application.configuration.Configuration.class)
                                        .withIgnoredProperties(
                                                "applicationDescription",  // Ignoré
                                                "additionalFiles",         // Non branché
                                                "hierarchicalNodes",       // Utilisé pour actions, pas validation
                                                "requiredAuthorizationsAttributes"  // Utilisé pour actions
                                        )
                                        .build()
                        )
                        .registerValueObject(
                                ValueObjectDefinitionBuilder
                                        .valueObjectDefinition(Internationalizations.class)
                                        .withIgnoredProperties(
                                                "tags",           // Ignoré pour le moment
                                                "application",    // Ignoré
                                                "rightsrequest",  // Ignoré
                                                "additionalFiles" // Ignoré
                                        )
                                        // On garde uniquement "data" qui contient i18nDisplayPattern
                                        .build()
                        )
                        .registerValueObject(
                                ValueObjectDefinitionBuilder
                                        .valueObjectDefinition(InternationalizationData.class)
                                        .withIgnoredProperties(
                                                "validations",
                                                "exceptions",
                                                "components",
                                                "submissions",
                                                "i18n"  // On ignore, seul i18nDisplayPattern compte
                                        )
                                        // On garde uniquement "i18nDisplayPattern"
                                        .build()
                        )
                        .build();
    }
}