package fr.inra.oresing.domain.application.configuration.migration.rules;

import fr.inra.oresing.domain.application.configuration.migration.action.AddAuthorizationScopeAttributeAction;
import fr.inra.oresing.domain.application.configuration.migration.action.CreateIndexesForDataAction;
import fr.inra.oresing.domain.application.configuration.migration.action.SaveConfigurationAction;
import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.change.DataAdded;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;

@Rule(
        name = DataAdded.NAME,
        description = DataAdded.DESCRIPTION,
        priority = DataAdded.PRIORITY)
public record DataAddedRule() {
    @Condition
    public boolean when(@Fact("change") ConfigurationChange change) {
        return change instanceof DataAdded;
    }

    @Action
    public void then(
            @Fact("change") DataAdded dataAdded,
            @Fact("migrationPlan") MigrationPlan plan,
            @Fact("context") MigrationContext context
    ) {
        // CORE : Sauvegarder la nouvelle configuration
        plan.addCoreAction(new SaveConfigurationAction(
                "save-config",
                context.newApplication()
        ));

        // POST : Ajouter le champ dans requiredAuthorizations (type composite)
        plan.addPostAction(
                new AddAuthorizationScopeAttributeAction(
                        "add-auth-attr-" + dataAdded.dataName(),
                        dataAdded.dataName()
                ));

        // POST : Créer les index pour ce nouveau data
        plan.addPostAction(new CreateIndexesForDataAction(
                "create-indexes-" + dataAdded.dataName(),
                dataAdded.dataName(),
                dataAdded.dataDescription()
        ));

        // Statut : Opération permise sans confirmation
        plan.setStatus(MigrationStatus.APPROVED);
    }
}