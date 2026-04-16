package fr.inra.oresing.domain.application.configuration.migration.rules;

import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.change.IgnorableChange;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;

@Rule(
        name = "IgnorableChange",
        description = "Ignore les changements automatiquement gérés",
        priority = 2  // ⬅️ Priorité 2 (après UnresolvableChange)
)
public class IgnorableRule {
    @Condition
    public boolean when(@Fact("change") ConfigurationChange change) {
        return change instanceof IgnorableChange;
    }

    @Action
    public void then(
            @Fact("change") IgnorableChange change,
            @Fact("migrationPlan") MigrationPlan plan
    ) {
        //doNothing
    }
}