package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record CreateIndexesForDataAction(
        String id,
        String dataName,
        StandardDataDescription dataDescription
) implements MigrationAction {

    private static final Logger log = LoggerFactory.getLogger(CreateIndexesForDataAction.class);

    @Override
    public ActionPhase phase() { return ActionPhase.POST; }

    @Override
    public boolean requiresUserConfirmation() { return false; }

    @Override
    public String description() { return "Créer les index pour " + dataName; }

    @Override
    public void execute(MigrationContext context) {
        context.authenticationPort().activateAdminRole();
        context.migrationApplicationPort().updateAuthorizationIndexes(context.newApplication());
        context.authenticationPort().setRoleForClient();
        log.info("CreateIndexesForDataAction for {} : {}", context.applicationName(), dataName());
    }
}