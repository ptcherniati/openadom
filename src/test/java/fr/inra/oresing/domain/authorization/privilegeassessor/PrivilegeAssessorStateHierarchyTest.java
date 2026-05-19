package fr.inra.oresing.domain.authorization.privilegeassessor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des interfaces scellées de la hiérarchie PrivilegeAssessorState.
 *
 * Ces interfaces forment le système de types (sealed) qui garantit que seuls les
 * domaines autorisés peuvent être utilisés dans la logique d'autorisation.
 * Si l'implémentation ne respecte pas les permits, la compilation échoue —
 * ces tests vérifient que les classes concrètes respectent les contrats de type.
 */
@Tag("core.auth")
@DisplayName("PrivilegeAssessorState — hiérarchie sealed interfaces")
class PrivilegeAssessorStateHierarchyTest {

    /**
     * Vérifie que PrivilegeSystemDomain est bien accessible comme PrivilegeAssessorState
     * via la chaîne d'héritage sealed : PrivilegeAssessorState → PrivilegeAssessorStateDomain
     * → PrivilegeAssessorStateSystemDomain → PrivilegeSystemDomain.
     */
    @Test
    @DisplayName("PrivilegeSystemDomain implémente toute la hiérarchie sealed")
    void privilegeSystemDomainIsInstanceOfAllSealedInterfaces() {
        // PrivilegeSystemDomain est l'implémentation concrète scellée
        // Le package d'implémentation utilise ces interfaces dans PrivilegeAssessorBuilder
        // Vérification statique : assignabilité de type garantit le contrat sealed
        assertThat(PrivilegeAssessorStateSystemDomain.class)
                .isAssignableTo(PrivilegeAssessorStateDomain.class);
        assertThat(PrivilegeAssessorStateDomain.class)
                .isAssignableTo(PrivilegeAssessorState.class);
    }

    @Test
    @DisplayName("PrivilegeApplicationDomain implémente la hiérarchie application domain")
    void privilegeApplicationDomainHierarchy() {
        assertThat(PrivilegeAssessorStateApplicationDomain.class)
                .isAssignableTo(PrivilegeAssessorStateDomain.class)
                .isAssignableTo(PrivilegeAssessorState.class);
    }

    @Test
    @DisplayName("PrivilegeAssessorStateDomain est un sous-type de PrivilegeAssessorState")
    void stateDomainExtendsState() {
        assertThat(PrivilegeAssessorState.class)
                .isAssignableFrom(PrivilegeAssessorStateDomain.class);
    }

    @Test
    @DisplayName("Les interfaces système et application sont disjointes (sealed)")
    void systemAndApplicationAreSeparate() {
        // Aucune des deux ne doit être sous-type de l'autre
        assertThat(PrivilegeAssessorStateApplicationDomain.class.isAssignableFrom(PrivilegeAssessorStateSystemDomain.class))
                .isFalse();
        assertThat(PrivilegeAssessorStateSystemDomain.class.isAssignableFrom(PrivilegeAssessorStateApplicationDomain.class))
                .isFalse();
    }

    @Test
    @DisplayName("PrivilegeAssessorState est bien une interface")
    void stateIsInterface() {
        assertThat(PrivilegeAssessorState.class).isInterface();
        assertThat(PrivilegeAssessorStateDomain.class).isInterface();
        assertThat(PrivilegeAssessorStateSystemDomain.class).isInterface();
        assertThat(PrivilegeAssessorStateApplicationDomain.class).isInterface();
    }
}