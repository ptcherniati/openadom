package fr.inra.oresing.rest.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link MigrationProperties}.
 *
 * <p>Vérifie le comportement par défaut (mode permissif = comportement historique)
 * et l'accessibilité des propriétés via les getters/setters Lombok.
 */
@Tag("core.config")
@DisplayName("MigrationProperties")
class MigrationPropertiesTest {

    @Test
    @DisplayName("bypassConfigurationCheck est true par défaut (comportement historique inchangé)")
    void defaultBypassConfigurationCheckIsTrue() {
        MigrationProperties props = new MigrationProperties();
        assertThat(props.isBypassConfigurationCheck())
                .as("Le mode permissif doit être activé par défaut pour préserver le comportement historique")
                .isTrue();
    }

    @Test
    @DisplayName("setBypassConfigurationCheck(false) passe en mode sécurisé")
    void setBypassConfigurationCheckFalseEnablesSecureMode() {
        MigrationProperties props = new MigrationProperties();
        props.setBypassConfigurationCheck(false);
        assertThat(props.isBypassConfigurationCheck()).isFalse();
    }

    @Test
    @DisplayName("setBypassConfigurationCheck(true) maintient le mode permissif")
    void setBypassConfigurationCheckTrueKeepsPermissiveMode() {
        MigrationProperties props = new MigrationProperties();
        props.setBypassConfigurationCheck(false); // mode sécurisé
        props.setBypassConfigurationCheck(true);  // retour mode permissif
        assertThat(props.isBypassConfigurationCheck()).isTrue();
    }

    @Test
    @DisplayName("Deux instances indépendantes ont des états indépendants")
    void twoInstancesAreIndependent() {
        MigrationProperties props1 = new MigrationProperties();
        MigrationProperties props2 = new MigrationProperties();
        props2.setBypassConfigurationCheck(false);

        assertThat(props1.isBypassConfigurationCheck()).isTrue();
        assertThat(props2.isBypassConfigurationCheck()).isFalse();
    }
}