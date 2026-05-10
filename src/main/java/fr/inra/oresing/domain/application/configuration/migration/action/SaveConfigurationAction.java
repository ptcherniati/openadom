package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SaveConfigurationAction(String id, Application newApplication) implements MigrationAction {

    private static final Logger log = LoggerFactory.getLogger(SaveConfigurationAction.class);

    @Override
    public ActionPhase phase() { return ActionPhase.CORE; }

    @Override
    public boolean requiresUserConfirmation() { return false; }

    @Override
    public String description() { return "Enregistrer la nouvelle configuration"; }

    @Override
    public void execute(MigrationContext context) {
        log.info("SaveConfigurationAction {} : {}", context.applicationName(), newApplication().getConfiguration().version());
        context.migrationApplicationPort().storeApplication(newApplication());
    }

    @Override
    public boolean isSingleton() { return true; }
}