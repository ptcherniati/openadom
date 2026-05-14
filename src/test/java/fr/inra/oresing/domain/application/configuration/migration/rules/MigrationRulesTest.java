package fr.inra.oresing.domain.application.configuration.migration.rules;

import fr.inra.oresing.domain.application.configuration.migration.change.DataAdded;
import fr.inra.oresing.domain.application.configuration.migration.change.IgnorableChange;
import fr.inra.oresing.domain.application.configuration.migration.change.NaturalKeyChanged;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour IgnorableRule et DataAddedRule.
 */
@Tag("core.config")
@Tag("domain.model")
@DisplayName("Migration rules – IgnorableRule et DataAddedRule")
class MigrationRulesTest {

    @Test
    @DisplayName("IgnorableRule.when() retourne true pour IgnorableChange")
    void ignorableRuleWhenTrue() {
        IgnorableRule rule = new IgnorableRule();
        assertThat(rule.when(new IgnorableChange())).isTrue();
    }

    @Test
    @DisplayName("IgnorableRule.when() retourne false pour un autre changement")
    void ignorableRuleWhenFalse() {
        IgnorableRule rule = new IgnorableRule();
        assertThat(rule.when(new NaturalKeyChanged())).isFalse();
    }

    @Test
    @DisplayName("IgnorableRule.then() ne fait rien (doNothing)")
    void ignorableRuleThen() {
        IgnorableRule rule = new IgnorableRule();
        MigrationPlan plan = new MigrationPlan(MigrationMode.DRY_RUN, Set.of(), MigrationStatus.PENDING);
        rule.then(new IgnorableChange(), plan);
        // doNothing – aucune action ajoutée
        assertThat(plan.totalActionsCount()).isZero();
    }

    @Test
    @DisplayName("DataAddedRule.when() retourne true pour DataAdded")
    void dataAddedRuleWhenTrue() {
        DataAddedRule rule = new DataAddedRule();
        DataAdded change = new DataAdded("myData", null);
        assertThat(rule.when(change)).isTrue();
    }

    @Test
    @DisplayName("DataAddedRule.when() retourne false pour un autre changement")
    void dataAddedRuleWhenFalse() {
        DataAddedRule rule = new DataAddedRule();
        assertThat(rule.when(new IgnorableChange())).isFalse();
    }
}