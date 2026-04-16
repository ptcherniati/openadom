package fr.inra.oresing.domain.application.configuration.migration.report;

import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationWarning;

import java.util.List;

public record MigrationReport(
        MigrationStatus status,
        List<MigrationAction> preActions,
        List<MigrationAction> coreActions,
        List<MigrationAction> postActions,
        List<MigrationWarning> warnings,
        boolean canExecuteAutomatically
) {

    public static MigrationReport from(MigrationPlan plan) {
        boolean canAuto = plan.warnings().stream()
                .filter(MigrationWarning.class::isInstance)
                .map(MigrationWarning.class::cast)
                .noneMatch(MigrationWarning::blocking);

        return new MigrationReport(
                plan.status().get(),
                List.copyOf(plan.preActions()),
                List.copyOf(plan.coreActions()),
                List.copyOf(plan.postActions()),
                List.copyOf(plan.warnings()),
                canAuto
        );
    }

    public static MigrationReport noChanges() {
        return new MigrationReport(
                MigrationStatus.NO_CHANGES,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
        );
    }

    public int totalActionsCount() {
        return preActions.size() + coreActions.size() + postActions.size();
    }

    public boolean hasBlockingWarnings() {
        return warnings.stream()
                .filter(MigrationWarning.class::isInstance)
                .map(MigrationWarning.class::cast)
                .anyMatch(MigrationWarning::blocking);
    }
}