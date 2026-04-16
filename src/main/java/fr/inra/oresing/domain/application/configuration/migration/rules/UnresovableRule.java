package fr.inra.oresing.domain.application.configuration.migration.rules;

import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.change.UnresolvableChange;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationWarning;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;

@Rule(
    name = "UnresolvableChange",
    description = "Bloque la migration pour les changements non gérés",
    priority = 1  // ⬅️ Priorité la plus haute
)
public record UnresovableRule() {

    @Condition
    public boolean when(@Fact("change") ConfigurationChange change) {
        return change instanceof UnresolvableChange;
    }

    @Action
    public void then(
        @Fact("change") UnresolvableChange change,
        @Fact("migrationPlan") MigrationPlan plan
    ) {
        // Créer un warning critique bloquant
        String warningId = "unresolvable-" + sanitize(change.propertyPath());

        plan.addWarning(MigrationWarning.critical(
            warningId,
            "Changement non supporté : " + change.propertyPath(),
            formatImpact(change)
        ));

        // Marquer le plan comme INTERDIT (non déblocable)
        plan.setStatus(MigrationStatus.FORBIDDEN);
    }

    /**
     * Formatte l'impact pour le message d'erreur
     */
    private String formatImpact(UnresolvableChange change) {
        StringBuilder impact = new StringBuilder();
        impact.append("Le système ne sait pas gérer ce type de modification.\n\n");
        impact.append("Détails du changement :\n");
        impact.append("  - Chemin : ").append(change.propertyPath()).append("\n");
        impact.append("  - Type : ").append(change.changeType()).append("\n");

        if (change.leftValue() != null) {
            impact.append("  - Ancienne valeur : ").append(change.leftValue()).append("\n");
        }
        if (change.rightValue() != null) {
            impact.append("  - Nouvelle valeur : ").append(change.rightValue()).append("\n");
        }

        return impact.toString();
    }

    /**
     * Sanitize le path pour créer un ID valide
     */
    private String sanitize(String path) {
        return path.replaceAll("[^a-zA-Z0-9_-]", "-");
    }
}