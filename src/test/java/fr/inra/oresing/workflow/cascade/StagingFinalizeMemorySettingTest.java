package fr.inra.oresing.workflow.cascade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du garde-fou {@link StagingFinalizeSql#isValidMemorySetting} ( S-5 ) :
 * seule une valeur de reglage memoire Postgres conforme est acceptee avant
 * interpolation dans un {@code SET LOCAL work_mem / maintenance_work_mem} .
 * Empeche toute injection SQL via la config .
 *
 * @author R.YAHIAOUI
 */
@Tag("domain.model")
@DisplayName("StagingFinalizeSql - validation reglage memoire ( S-5 )")
class StagingFinalizeMemorySettingTest {

    @Test
    @DisplayName("valeurs Postgres valides acceptees")
    void validSettings() {
        assertThat(StagingFinalizeSql.isValidMemorySetting("256MB")).isTrue();
        assertThat(StagingFinalizeSql.isValidMemorySetting("1GB")).isTrue();
        assertThat(StagingFinalizeSql.isValidMemorySetting("524288kB")).isTrue();
        assertThat(StagingFinalizeSql.isValidMemorySetting("65536")).isTrue();      // sans unite = kB Postgres
        assertThat(StagingFinalizeSql.isValidMemorySetting("2TB")).isTrue();
        assertThat(StagingFinalizeSql.isValidMemorySetting("  512MB  ")).isTrue();  // trim
    }

    @Test
    @DisplayName("valeurs invalides / injection rejetees")
    void invalidSettings() {
        assertThat(StagingFinalizeSql.isValidMemorySetting(null)).isFalse();
        assertThat(StagingFinalizeSql.isValidMemorySetting("")).isFalse();
        assertThat(StagingFinalizeSql.isValidMemorySetting("   ")).isFalse();
        assertThat(StagingFinalizeSql.isValidMemorySetting("256 MB")).isFalse();    // espace interne
        assertThat(StagingFinalizeSql.isValidMemorySetting("256Mb")).isFalse();     // unite mal casee
        assertThat(StagingFinalizeSql.isValidMemorySetting("big")).isFalse();
        assertThat(StagingFinalizeSql.isValidMemorySetting("256MB'; DROP TABLE referencevalue; --")).isFalse();
        assertThat(StagingFinalizeSql.isValidMemorySetting("-1MB")).isFalse();
    }
}
