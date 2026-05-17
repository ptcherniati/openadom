package fr.inra.oresing.domain.application.configuration.migration.change;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les records de changement de migration (0% de couverture dans SonarQube).
 */
@Tag("core.config")
@Tag("domain.model")
@DisplayName("Migration change records – instanciation et accesseurs")
class MigrationChangeRecordsTest {

    @Test
    @DisplayName("DataRemoved – constantes et accesseurs")
    void dataRemoved() {
        DataRemoved removed = new DataRemoved("myData", null);
        assertThat(removed.dataName()).isEqualTo("myData");
        assertThat(removed.dataDescription()).isNull();
        assertThat(DataRemoved.NAME).isEqualTo("DataRemoved");
        assertThat(DataRemoved.DESCRIPTION).isNotBlank();
    }

    @Test
    @DisplayName("I18nImportHeaderChange – instanciation")
    void i18nImportHeaderChange() {
        I18nImportHeaderChange change = new I18nImportHeaderChange();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChanged.class)
                .isInstanceOf(I18nChange.class);
    }

    @Test
    @DisplayName("I18nSimpleChange – instanciation")
    void i18nSimpleChange() {
        I18nSimpleChange change = new I18nSimpleChange();
        assertThat(change).isNotNull()
                .isInstanceOf(I18nChange.class);
    }

    @Test
    @DisplayName("NaturalKeyChanged – instanciation")
    void naturalKeyChanged() {
        NaturalKeyChanged change = new NaturalKeyChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("SubmissionChanged – instanciation")
    void submissionChanged() {
        SubmissionChanged change = new SubmissionChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("IgnorableChange – instanciation et interface ConfigurationChange")
    void ignorableChange() {
        IgnorableChange change = new IgnorableChange();
        assertThat(change).isNotNull()
                .isInstanceOf(ConfigurationChange.class);
    }

    @Test
    @DisplayName("AuthorizationChanged – instanciation et interfaces")
    void authorizationChanged() {
        AuthorizationChanged change = new AuthorizationChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChanged.class)
                .isInstanceOf(ComponentChange.class);
    }

    @Test
    @DisplayName("CheckerAdded – instanciation et CheckerChange")
    void checkerAdded() {
        CheckerAdded change = new CheckerAdded();
        assertThat(change).isNotNull()
                .isInstanceOf(CheckerChange.class)
                .isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("CheckerRemoved – instanciation et CheckerChange")
    void checkerRemoved() {
        CheckerRemoved change = new CheckerRemoved();
        assertThat(change).isNotNull()
                .isInstanceOf(CheckerChange.class)
                .isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("CheckerTypeChanged – instanciation et CheckerModified")
    void checkerTypeChanged() {
        CheckerTypeChanged change = new CheckerTypeChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(CheckerModified.class)
                .isInstanceOf(CheckerChange.class);
    }

    @Test
    @DisplayName("CheckerDefinitionChanged – instanciation et CheckerModified")
    void checkerDefinitionChanged() {
        CheckerDefinitionChanged change = new CheckerDefinitionChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(CheckerModified.class)
                .isInstanceOf(CheckerChange.class);
    }

    @Test
    @DisplayName("ComponentAdded – instanciation et ComponentChange")
    void componentAdded() {
        ComponentAdded change = new ComponentAdded();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChange.class)
                .isInstanceOf(DataChange.class);
    }

    @Test
    @DisplayName("ComponenRemoved – instanciation et ComponentChange")
    void componenRemoved() {
        ComponenRemoved change = new ComponenRemoved();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChange.class)
                .isInstanceOf(DataChange.class);
    }

    @Test
    @DisplayName("HierarchieChanged – instanciation et ComponentChanged")
    void hierarchieChanged() {
        HierarchieChanged change = new HierarchieChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("DataAdded – constantes et accesseurs")
    void dataAdded() {
        DataAdded added = new DataAdded("myNewData", null);
        assertThat(added.dataName()).isEqualTo("myNewData");
        assertThat(added.dataDescription()).isNull();
        assertThat(DataAdded.NAME).isEqualTo("DataAdded");
        assertThat(DataAdded.DESCRIPTION).isNotBlank();
        assertThat(DataAdded.PRIORITY).isEqualTo(10);
    }

    @Test
    @DisplayName("I18nDisplayPattenChanged – instanciation ComponentChanged et I18nChange")
    void i18nDisplayPattenChanged() {
        I18nDisplayPattenChanged change = new I18nDisplayPattenChanged();
        assertThat(change).isNotNull()
                .isInstanceOf(ComponentChanged.class)
                .isInstanceOf(I18nChange.class);
    }

    @Test
    @DisplayName("UnresolvableChange – accesseurs et interface ConfigurationChange")
    void unresolvableChange() {
        UnresolvableChange change = new UnresolvableChange("a.b", "modify", "old", "new");
        assertThat(change.propertyPath()).isEqualTo("a.b");
        assertThat(change.changeType()).isEqualTo("modify");
        assertThat(change.leftValue()).isEqualTo("old");
        assertThat(change.rightValue()).isEqualTo("new");
        assertThat(change).isInstanceOf(ConfigurationChange.class);
    }

    @Test
    @DisplayName("ConfigurationChange.FactType – toutes les valeurs")
    void configurationChangeFactType() {
        assertThat(ConfigurationChange.FactType.values()).hasSize(3);
        assertThat(ConfigurationChange.FactType.ADD).isNotNull();
        assertThat(ConfigurationChange.FactType.REMOVE).isNotNull();
        assertThat(ConfigurationChange.FactType.MODIFY).isNotNull();
    }
}