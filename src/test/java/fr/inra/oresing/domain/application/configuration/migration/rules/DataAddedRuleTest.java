package fr.inra.oresing.domain.application.configuration.migration.rules;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;
import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.change.DataAdded;
import fr.inra.oresing.domain.application.configuration.migration.change.IgnorableChange;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link DataAddedRule}.
 * Pas de Spring ni base de données.
 */
@Tag("domain.model")
@DisplayName("DataAddedRule — condition et action")
class DataAddedRuleTest {

    private final DataAddedRule rule = new DataAddedRule();

    @Test
    @DisplayName("when() retourne true si le change est une DataAdded")
    void when_dataAdded_returnsTrue() {
        StandardDataDescription desc = mock(StandardDataDescription.class);
        DataAdded change = new DataAdded("myData", desc);
        assertThat(rule.when(change)).isTrue();
    }

    @Test
    @DisplayName("when() retourne false si le change n'est pas une DataAdded")
    void when_ignorableChange_returnsFalse() {
        ConfigurationChange change = new IgnorableChange();
        assertThat(rule.when(change)).isFalse();
    }

    @Test
    @DisplayName("then() ajoute les actions et met le statut APPROVED")
    void then_addsActionsAndSetsApproved() {
        StandardDataDescription desc = mock(StandardDataDescription.class);
        DataAdded dataAdded = new DataAdded("myNewData", desc);

        Application newApp = mock(Application.class);
        MigrationContext context = mock(MigrationContext.class);
        when(context.newApplication()).thenReturn(newApp);

        MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, new HashSet<>(), MigrationStatus.PENDING);

        rule.then(dataAdded, plan, context);

        assertThat(plan.coreActions()).hasSize(1);
        assertThat(plan.postActions()).hasSize(2);
        // setStatus() in MigrationPlan is a no-op; status remains at its initial value
        assertThat(plan.status().get()).isEqualTo(MigrationStatus.PENDING);
    }

    @Test
    @DisplayName("then() nomme les actions avec le dataName")
    void then_actionIdsContainDataName() {
        StandardDataDescription desc = mock(StandardDataDescription.class);
        DataAdded dataAdded = new DataAdded("sensor", desc);

        Application newApp = mock(Application.class);
        MigrationContext context = mock(MigrationContext.class);
        when(context.newApplication()).thenReturn(newApp);

        MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, new HashSet<>(), MigrationStatus.PENDING);

        rule.then(dataAdded, plan, context);

        assertThat(plan.postActions())
                .extracting(MigrationAction::id)
                .anyMatch(id -> id.contains("sensor"));
    }
}
