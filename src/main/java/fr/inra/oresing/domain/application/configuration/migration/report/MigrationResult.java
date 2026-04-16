package fr.inra.oresing.domain.application.configuration.migration.report;

import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.change.UnresolvableChange;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationWarning;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Résultat de l'exécution de la migration
 */
public record MigrationResult(
        MigrationStatus status,
        int executedActionsCount,
        Duration duration,
        LocalDateTime executedAt,
        List<MigrationWarning> remainingWarnings,
        String errorMessage
) {

    public static MigrationResult success(MigrationPlan plan, Duration duration) {
        return new MigrationResult(
                MigrationStatus.EXECUTED,
                plan.totalActionsCount(),
                duration, // À calculer dans l'executor
                LocalDateTime.now(),
                List.of(),
                null
        );
    }

    public static MigrationResult blocked(List<MigrationWarning> warnings) {
        return new MigrationResult(
                MigrationStatus.REQUIRES_CONFIRMATION,
                0,
                Duration.ZERO,
                LocalDateTime.now(),
                List.copyOf(warnings),
                "Migration bloquée : confirmations utilisateur requises"
        );
    }

    public static MigrationResult failure(Exception e, MigrationPlan plan) {
        return new MigrationResult(
                MigrationStatus.FAILED,
                plan.actionIds().size(),
                Duration.ZERO,
                LocalDateTime.now(),
                List.of(),
                e.getMessage()
        );
    }

    public static MigrationResult noChanges() {
        return new MigrationResult(
                MigrationStatus.NO_CHANGES,
                0,
                Duration.ZERO,
                LocalDateTime.now(),
                List.of(),
                null
        );
    }

    public static MigrationResult dryRunSuccess(MigrationPlan plan) {
        return new MigrationResult(
                MigrationStatus.APPROVED,
                plan.totalActionsCount(),       // Nombre d'actions qui auraient été exécutées
                Duration.ZERO,                  // Pas de temps d'exécution
                LocalDateTime.now(),            // Timestamp
                List.of(),                      // Pas de warnings restants
                "Dry-run mode: actions not executed"  // Message explicatif
        );
    }

    public static MigrationResult onError(List<ConfigurationChange> changes) {
        return new MigrationResult(
                MigrationStatus.FAILED,
                0,
                Duration.ZERO,
                LocalDateTime.now(),
                List.of(),
                changes.stream()
                        .filter(UnresolvableChange.class::isInstance)
                        .map(UnresolvableChange.class::cast)
                        .map(UnresolvableChange::propertyPath)
                        .collect(Collectors.joining("\n"))
        );
    }


    public boolean isSuccess() {
        return status == MigrationStatus.EXECUTED;
    }
}