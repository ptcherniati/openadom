package fr.inra.oresing.domain.application.configuration.migration.context;

import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import org.jeasy.rules.api.Facts;

public record FactKeys() {
    public static final String CHANGE = "change";
    public static final String MIGRATION_PLAN = "migrationPlan";
    public static final String CONTEXT = "context";

    public static final Facts buildFacts(
            ConfigurationChange change,
            MigrationPlan plan,
            MigrationContext context) {
        Facts facts = new Facts();
        facts.put(CHANGE, change);
        facts.put(MIGRATION_PLAN, plan);
        facts.put(CONTEXT, context);
        return facts;
    }
}