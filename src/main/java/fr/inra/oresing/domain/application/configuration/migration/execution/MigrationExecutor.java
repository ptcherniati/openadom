package fr.inra.oresing.domain.application.configuration.migration.execution;

import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Exécuteur de plan de migration.
 * Exécute les actions du plan dans l'ordre : PRE → CORE → POST
 */
@Component
public class MigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(MigrationExecutor.class);

    /**
     * Exécute le plan de migration
     *
     * @param plan Le plan à exécuter
     * @param context Le contexte de migration
     * @return Le résultat de l'exécution
     */
    public MigrationResult execute(MigrationPlan plan, MigrationContext context) {
        log.info("⚡ Starting migration execution");
        log.info("   Mode: {}", plan.migrationMode());
        log.info("   Status: {}", plan.status());

        Instant start = Instant.now();

        try {
            // Vérifier le mode
            if (plan.isDryRun()) {
                log.info("❌ DRY_RUN mode - skipping execution");
                return MigrationResult.dryRunSuccess(plan);
            }

            log.info("✅ EXECUTE mode - running actions");

            // Exécuter les actions par phase
            executePhase(plan.preActions(), context, ActionPhase.PRE);
            executePhase(plan.coreActions(), context, ActionPhase.CORE);
            executePhase(plan.postActions(), context, ActionPhase.POST);

            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ Migration completed successfully in {}ms", duration.toMillis());

            return MigrationResult.success(plan, duration);

        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.error("❌ Migration failed after {}ms: {}", duration.toMillis(), e.getMessage(), e);
            return MigrationResult.failure(e, plan);
        }
    }

    /**
     * Exécute une phase (PRE, CORE ou POST)
     */
    private void executePhase(
            List<MigrationAction> actions,
            MigrationContext context,
            ActionPhase phase
    ) {
        if (actions.isEmpty()) {
            log.info("  ⏭ Phase {} - no actions", phase);
            return;
        }

        log.info("=== Executing {} phase ({} actions) ===", phase, actions.size());

        for (MigrationAction action : actions) {
            executeAction(action, context);
        }
    }

    /**
     * Exécute une action individuelle
     */
    private void executeAction(MigrationAction action, MigrationContext context) {
        if (log.isInfoEnabled()) {
            log.info("  ▶ Executing: {} [{}]", action.id(), action.phase());
        }
        if (log.isDebugEnabled()) {
            log.debug("     Description: {}", action.description());
        }

        try {
            action.execute(context);
            log.info("    ✅ Success");
        } catch (Exception e) {
            throw new MigrationExecutionException(
                    "Échec de l'action " + action.id() + ": " + e.getMessage(),
                    action,
                    e
            );
        }
    }
}