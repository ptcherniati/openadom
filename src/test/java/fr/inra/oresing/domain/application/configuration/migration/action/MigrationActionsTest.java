package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs des records d'action de migration.
 * Couvre les métadonnées (phase, requiresUserConfirmation, description, id)
 * sans déclencher execute() qui nécessite un contexte Spring.
 */
@DisplayName("MigrationAction record types — métadonnées")
@Tag("core.config")
@Tag("domain.model")
class MigrationActionsTest {

    // ------------------------------------------------------------------ //
    //  SaveConfigurationAction                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("SaveConfigurationAction")
    class SaveConfigurationActionTest {

        @Test
        @DisplayName("phase() retourne CORE")
        void phase() {
            assertThat(new SaveConfigurationAction("save-1", null).phase())
                    .isEqualTo(ActionPhase.CORE);
        }

        @Test
        @DisplayName("requiresUserConfirmation() est false")
        void noUserConfirmation() {
            assertThat(new SaveConfigurationAction("save-1", null).requiresUserConfirmation())
                    .isFalse();
        }

        @Test
        @DisplayName("description() retourne une chaîne non vide")
        void description() {
            assertThat(new SaveConfigurationAction("save-1", null).description())
                    .isNotBlank();
        }

        @Test
        @DisplayName("id() retourne le bon identifiant")
        void id() {
            assertThat(new SaveConfigurationAction("my-save", null).id())
                    .isEqualTo("my-save");
        }

        @Test
        @DisplayName("isSingleton() retourne true (override)")
        void isSingleton() {
            assertThat(new SaveConfigurationAction("s", null).isSingleton())
                    .isTrue();
        }

        @Test
        @DisplayName("equals et hashCode cohérents (record)")
        void equalsHashCode() {
            SaveConfigurationAction a = new SaveConfigurationAction("x", null);
            SaveConfigurationAction b = new SaveConfigurationAction("x", null);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    // ------------------------------------------------------------------ //
    //  AddAuthorizationScopeAttributeAction                              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AddAuthorizationScopeAttributeAction")
    class AddAuthorizationScopeAttributeActionTest {

        @Test
        @DisplayName("phase() retourne POST")
        void phase() {
            assertThat(new AddAuthorizationScopeAttributeAction("add-1", "myData").phase())
                    .isEqualTo(ActionPhase.POST);
        }

        @Test
        @DisplayName("requiresUserConfirmation() est false")
        void noUserConfirmation() {
            assertThat(new AddAuthorizationScopeAttributeAction("add-1", "myData").requiresUserConfirmation())
                    .isFalse();
        }

        @Test
        @DisplayName("description() contient le nom du type de données")
        void descriptionContainsDataName() {
            AddAuthorizationScopeAttributeAction action =
                    new AddAuthorizationScopeAttributeAction("add-scope", "mesures");
            assertThat(action.description()).contains("mesures");
        }

        @Test
        @DisplayName("id() retourne l'identifiant construit")
        void id() {
            assertThat(new AddAuthorizationScopeAttributeAction("my-id", "d").id())
                    .isEqualTo("my-id");
        }

        @Test
        @DisplayName("dataName() retourne le nom du type de données")
        void dataName() {
            assertThat(new AddAuthorizationScopeAttributeAction("id", "refData").dataName())
                    .isEqualTo("refData");
        }

        @Test
        @DisplayName("isSingleton() retourne false par défaut")
        void isSingletonDefault() {
            assertThat(new AddAuthorizationScopeAttributeAction("id", "d").isSingleton())
                    .isFalse();
        }

        @Test
        @DisplayName("toString() générée par record")
        void toStringNotNull() {
            assertThat(new AddAuthorizationScopeAttributeAction("aid", "dt").toString())
                    .isNotNull()
                    .contains("AddAuthorizationScopeAttributeAction");
        }
    }

    // ------------------------------------------------------------------ //
    //  CreateIndexesForDataAction                                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("CreateIndexesForDataAction")
    class CreateIndexesForDataActionTest {

        @Test
        @DisplayName("phase() retourne POST")
        void phase() {
            assertThat(new CreateIndexesForDataAction("idx-1", "myData", null).phase())
                    .isEqualTo(ActionPhase.POST);
        }

        @Test
        @DisplayName("requiresUserConfirmation() est false")
        void noUserConfirmation() {
            assertThat(new CreateIndexesForDataAction("idx-1", "myData", null).requiresUserConfirmation())
                    .isFalse();
        }

        @Test
        @DisplayName("description() contient le nom du type de données")
        void descriptionContainsDataName() {
            CreateIndexesForDataAction action = new CreateIndexesForDataAction("idx", "refType", null);
            assertThat(action.description()).contains("refType");
        }

        @Test
        @DisplayName("id() retourne l'identifiant")
        void id() {
            assertThat(new CreateIndexesForDataAction("create-idx", "d", null).id())
                    .isEqualTo("create-idx");
        }

        @Test
        @DisplayName("dataName() retourne le nom du type de données")
        void dataName() {
            assertThat(new CreateIndexesForDataAction("id", "myRef", null).dataName())
                    .isEqualTo("myRef");
        }

        @Test
        @DisplayName("isSingleton() retourne false par défaut (interface default)")
        void isSingletonDefault() {
            assertThat(new CreateIndexesForDataAction("id", "d", null).isSingleton())
                    .isFalse();
        }
    }

    // ------------------------------------------------------------------ //
    //  UpdateAuthorizationScopeAction (package-private)                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("UpdateAuthorizationScopeAction")
    class UpdateAuthorizationScopeActionTest {

        @Test
        @DisplayName("phase() retourne CORE")
        void phase() {
            assertThat(new UpdateAuthorizationScopeAction("upd-1").phase())
                    .isEqualTo(ActionPhase.CORE);
        }

        @Test
        @DisplayName("requiresUserConfirmation() est false")
        void noUserConfirmation() {
            assertThat(new UpdateAuthorizationScopeAction("upd-1").requiresUserConfirmation())
                    .isFalse();
        }

        @Test
        @DisplayName("description() retourne une chaîne non vide")
        void description() {
            assertThat(new UpdateAuthorizationScopeAction("upd-1").description())
                    .isNotBlank();
        }

        @Test
        @DisplayName("id() retourne l'identifiant")
        void id() {
            assertThat(new UpdateAuthorizationScopeAction("my-upd").id())
                    .isEqualTo("my-upd");
        }

        @Test
        @DisplayName("execute() est no-op — aucune exception levée")
        void executeIsNoOp() {
            // UpdateAuthorizationScopeAction.execute() ne fait rien
            UpdateAuthorizationScopeAction action = new UpdateAuthorizationScopeAction("noop");
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> action.execute(null)
            );
        }
    }

    // ------------------------------------------------------------------ //
    //  MigrationAction interface — méthode default isSingleton()         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("MigrationAction — interface default methods")
    class MigrationActionInterfaceTest {

        @Test
        @DisplayName("isSingleton() vaut false par défaut pour une action non-singleton")
        void defaultIsSingletonIsFalse() {
            // On utilise AddAuthorizationScopeAttributeAction qui ne surcharge pas isSingleton()
            MigrationAction action = new AddAuthorizationScopeAttributeAction("id", "data");
            assertThat(action.isSingleton()).isFalse();
        }

        @Test
        @DisplayName("isSingleton() vaut true pour SaveConfigurationAction qui le surcharge")
        void overriddenIsSingletonIsTrue() {
            MigrationAction action = new SaveConfigurationAction("id", null);
            assertThat(action.isSingleton()).isTrue();
        }
    }
}