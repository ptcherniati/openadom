package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;

record UpdateAuthorizationScopeAction(String id) implements MigrationAction {

    @Override
    public ActionPhase phase() {
        return ActionPhase.CORE;
    }

    @Override
    public boolean requiresUserConfirmation() {
        return false;
    }

    @Override
    public String description() {
        return "UpdateAuthorizationScopeAction";
    }

    @Override
    public void execute(MigrationContext context) {
        // Non implémenté : la reconstruction des scopes est gérée en aval par le service appelant
    }
}