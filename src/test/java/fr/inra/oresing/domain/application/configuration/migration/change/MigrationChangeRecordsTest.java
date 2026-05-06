package fr.inra.oresing.domain.application.configuration.migration.change;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les records de changement de migration (0% de couverture dans SonarQube).
 */
@Tag("core.config")
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
        assertThat(change).isNotNull();
        assertThat(change).isInstanceOf(ComponentChanged.class);
        assertThat(change).isInstanceOf(I18nChange.class);
    }

    @Test
    @DisplayName("I18nSimpleChange – instanciation")
    void i18nSimpleChange() {
        I18nSimpleChange change = new I18nSimpleChange();
        assertThat(change).isNotNull();
        assertThat(change).isInstanceOf(I18nChange.class);
    }

    @Test
    @DisplayName("NaturalKeyChanged – instanciation")
    void naturalKeyChanged() {
        NaturalKeyChanged change = new NaturalKeyChanged();
        assertThat(change).isNotNull();
        assertThat(change).isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("SubmissionChanged – instanciation")
    void submissionChanged() {
        SubmissionChanged change = new SubmissionChanged();
        assertThat(change).isNotNull();
        assertThat(change).isInstanceOf(ComponentChanged.class);
    }

    @Test
    @DisplayName("IgnorableChange – instanciation et interface ConfigurationChange")
    void ignorableChange() {
        IgnorableChange change = new IgnorableChange();
        assertThat(change).isNotNull();
        assertThat(change).isInstanceOf(ConfigurationChange.class);
    }
}