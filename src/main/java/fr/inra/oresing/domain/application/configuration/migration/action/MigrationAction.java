package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;

public sealed interface MigrationAction permits AddAuthorizationScopeAttributeAction,  CreateIndexesForDataAction, SaveConfigurationAction, UpdateAuthorizationScopeAction {

    String id();
    ActionPhase phase();
    boolean requiresUserConfirmation();

    String description();

    // Exécution conditionnelle selon le mode
    void execute(MigrationContext context);
    default boolean isSingleton() {
        return false;
    }
}