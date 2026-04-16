package fr.inra.oresing.domain.application.configuration.migration.plan;

public enum MigrationStatus {
    PENDING,                  // En cours d'analyse
    APPROVED,                 // Peut être exécuté sans confirmation
    REQUIRES_CONFIRMATION,    // Bloqué, attend confirmation utilisateur
    FORBIDDEN,                // Impossible à exécuter
    EXECUTED,
    FAILED,
    NO_CHANGES// Migration effectuée
}