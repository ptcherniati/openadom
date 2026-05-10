package fr.inra.oresing.domain.application.configuration.migration.context;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.port.AuthenticationPort;
import fr.inra.oresing.domain.port.MigrationApplicationPort;

/**
 * Contexte d'exécution d'un plan de migration.
 * <p>
 * Objet domaine pur : zéro dépendance sur {@code rest.*}, {@code persistence.*} ou Spring.
 * Les opérations techniques (authentification, persistance) passent par leurs ports respectifs.
 */
public record MigrationContext(
        MigrationApplicationPort migrationApplicationPort,
        AuthenticationPort authenticationPort,
        String applicationName,
        Application oldApplication,
        Application newApplication,
        SchemaInfo schemaInfo,
        DataInfo dataInfo
) {
}