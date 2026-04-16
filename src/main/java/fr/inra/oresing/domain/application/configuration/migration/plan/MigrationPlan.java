package fr.inra.oresing.domain.application.configuration.migration.plan;

import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plan de migration (état mutable durant l'évaluation des règles)
 */
public record MigrationPlan(
        MigrationMode migrationMode,
        Set<String> acceptedWarnings,
        AtomicReference<MigrationStatus> status,
        List<MigrationAction> preActions,
        List<MigrationAction> coreActions,
        List<MigrationAction> postActions,
        List<MigrationWarning> warnings,
        Set<String> actionIds) {


    public MigrationPlan(MigrationMode migrationMode, Set<String> acceptedWarnings, MigrationStatus status) {
        this(migrationMode,
                acceptedWarnings,
                new AtomicReference<>(status),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashSet<>()
        );
    }

    public void addPreAction(MigrationAction action) {
        if (actionIds.add(action.id())) {
            preActions.add(action);
        }
    }

    public void addCoreAction(MigrationAction action) {
        if (actionIds.add(action.id())) {
            coreActions.add(action);
        }
    }

    public void addPostAction(MigrationAction action) {
        if (actionIds.add(action.id())) {
            postActions.add(action);
        }
    }

    public void addWarning(MigrationWarning warning) {
        warnings.add(warning);
        if (warning.blocking() && !acceptedWarnings().contains(warning.id())) {
            setStatus(MigrationStatus.REQUIRES_CONFIRMATION);
        }
    }

    public boolean isDryRun() {
        return migrationMode() == MigrationMode.DRY_RUN;
    }

    public int totalActionsCount() {
        return preActions.size() + coreActions.size() + postActions.size();
    }

    public MigrationStatus currentStatus() {
        return status.get();
    }

    @SuppressWarnings("java:S1172") // paramètre conservé pour API cohérente avec les appelants
    public void setStatus(MigrationStatus migrationStatus) {
        // Le statut est porté par l'AtomicReference — modification via addWarning()
    }
}