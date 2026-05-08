package fr.inra.oresing.domain.application.configuration.migration.change;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des records de migration change supplémentaires :
 * NaturalKeyChanged, SubmissionChanged, DataRemoved, I18nSimpleChange, I18nImportHeaderChange.
 */
@Tag("domain.model")
@DisplayName("Migration change records supplémentaires")
class MigrationChangeRecordsExtraTest {

    @Test
    @DisplayName("NaturalKeyChanged – instanciation ComponentChanged")
    void naturalKeyChanged() {
        NaturalKeyChanged c = new NaturalKeyChanged();
        assertThat(c).isNotNull();
        assertThat(c).isInstanceOf(ComponentChanged.class);
        assertThat(c).isInstanceOf(ConfigurationChange.class);
    }

    @Test
    @DisplayName("SubmissionChanged – instanciation ComponentChanged")
    void submissionChanged() {
        SubmissionChanged c = new SubmissionChanged();
        assertThat(c).isNotNull();
        assertThat(c).isInstanceOf(ComponentChanged.class);
        assertThat(c).isInstanceOf(ConfigurationChange.class);
    }

    @Test
    @DisplayName("DataRemoved – constantes et accesseurs")
    void dataRemoved() {
        DataRemoved d = new DataRemoved("removedType", null);
        assertThat(d.dataName()).isEqualTo("removedType");
        assertThat(d.dataDescription()).isNull();
        assertThat(DataRemoved.NAME).isEqualTo("DataRemoved");
        assertThat(DataRemoved.DESCRIPTION).isNotBlank();
        assertThat(d).isInstanceOf(DataChange.class);
        assertThat(d).isInstanceOf(ConfigurationChange.class);
    }

    @Test
    @DisplayName("I18nSimpleChange – instanciation I18nChange")
    void i18nSimpleChange() {
        I18nSimpleChange c = new I18nSimpleChange();
        assertThat(c).isNotNull();
        assertThat(c).isInstanceOf(I18nChange.class);
        assertThat(c).isInstanceOf(ConfigurationChange.class);
    }

    @Test
    @DisplayName("I18nImportHeaderChange – instanciation ComponentChanged et I18nChange")
    void i18nImportHeaderChange() {
        I18nImportHeaderChange c = new I18nImportHeaderChange();
        assertThat(c).isNotNull();
        assertThat(c).isInstanceOf(ComponentChanged.class);
        assertThat(c).isInstanceOf(I18nChange.class);
        assertThat(c).isInstanceOf(ConfigurationChange.class);
    }
}
