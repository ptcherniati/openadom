package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ConfigHashService} - safety check du routage
 * lite vs FULL au republish .
 */
class ConfigHashServiceTest {

    private final ConfigHashService service = new ConfigHashService();

    @Nested
    @DisplayName("computeHash")
    class ComputeHash {

        @Test
        @DisplayName("retourne empty si application null")
        void nullApp() {
            assertThat(service.computeHash(null, "dt")).isEmpty();
        }

        @Test
        @DisplayName("retourne empty si dataName null")
        void nullDataName() {
            Application app = mock(Application.class);
            assertThat(service.computeHash(app, null)).isEmpty();
        }

        @Test
        @DisplayName("retourne empty si datatype absent de la config")
        void datatypeMissing() {
            Application app = mock(Application.class);
            Configuration config = mock(Configuration.class);
            when(app.getConfiguration()).thenReturn(config);
            when(config.dataDescription()).thenReturn(new LinkedHashMap<>());
            assertThat(service.computeHash(app, "inexistant")).isEmpty();
        }

        @Test
        @DisplayName("retourne hash hex 64 chars pour un datatype valide")
        void hashFormat() {
            Application app = appWithDatatype("dt1");
            Optional<String> hash = service.computeHash(app, "dt1");
            assertThat(hash).isPresent();
            assertThat(hash.get()).hasSize(64).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("meme description -> meme hash (idempotence)")
        void idempotent() {
            Application app1 = appWithDatatype("dt1");
            Application app2 = appWithDatatype("dt1");
            assertThat(service.computeHash(app1, "dt1"))
                    .isEqualTo(service.computeHash(app2, "dt1"));
        }

        @Test
        @DisplayName("Set<Tag> order-insensitive : meme contenu insere dans des HashSet differents -> meme hash")
        void setOrderInsensitive() {
            // Simule le bug originel : entre 2 runs JVM , un meme jeu de Tags
            // peut etre re-construit avec un iteration order different ( HashSet
            // bucket distribution non garantie stable cross-JVM ) . Le hash
            // canonique doit etre IDENTIQUE quelle que soit l'ordre interne .
            java.util.Set<fr.inra.oresing.domain.application.configuration.Tag> tagsA = new java.util.LinkedHashSet<>();
            tagsA.add(new fr.inra.oresing.domain.application.configuration.Tag.DataTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.DATA_TAG));
            tagsA.add(new fr.inra.oresing.domain.application.configuration.Tag.HiddenTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.HIDDEN_TAG));
            tagsA.add(new fr.inra.oresing.domain.application.configuration.Tag.ReferenceTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.REFFERENCE_TAG));

            java.util.Set<fr.inra.oresing.domain.application.configuration.Tag> tagsB = new java.util.LinkedHashSet<>();
            tagsB.add(new fr.inra.oresing.domain.application.configuration.Tag.ReferenceTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.REFFERENCE_TAG));
            tagsB.add(new fr.inra.oresing.domain.application.configuration.Tag.DataTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.DATA_TAG));
            tagsB.add(new fr.inra.oresing.domain.application.configuration.Tag.HiddenTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.HIDDEN_TAG));

            Application appA = appWithDatatypeAndTags("dt", tagsA);
            Application appB = appWithDatatypeAndTags("dt", tagsB);

            assertThat(service.computeHash(appA, "dt"))
                    .as("Hash doit etre insensible a l'ordre du Set<Tag>")
                    .isEqualTo(service.computeHash(appB, "dt"));
        }

        @Test
        @DisplayName("contenu Set different -> hash different")
        void differentSetContentGivesDifferentHash() {
            java.util.Set<fr.inra.oresing.domain.application.configuration.Tag> tagsA = new java.util.LinkedHashSet<>();
            tagsA.add(new fr.inra.oresing.domain.application.configuration.Tag.DataTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.DATA_TAG));

            java.util.Set<fr.inra.oresing.domain.application.configuration.Tag> tagsB = new java.util.LinkedHashSet<>();
            tagsB.add(new fr.inra.oresing.domain.application.configuration.Tag.HiddenTag(
                    fr.inra.oresing.domain.application.configuration.Tag.TagDefinitions.HIDDEN_TAG));

            Application appA = appWithDatatypeAndTags("dt", tagsA);
            Application appB = appWithDatatypeAndTags("dt", tagsB);

            assertThat(service.computeHash(appA, "dt"))
                    .as("Hash doit changer si le contenu du Set change")
                    .isNotEqualTo(service.computeHash(appB, "dt"));
        }
    }

    @Nested
    @DisplayName("configUnchangedSinceUpload")
    class Unchanged {

        @Test
        @DisplayName("storedHash null -> false (force FULL)")
        void nullStored() {
            Application app = appWithDatatype("dt1");
            assertThat(service.configUnchangedSinceUpload(app, "dt1", null)).isFalse();
        }

        @Test
        @DisplayName("storedHash blank -> false (force FULL)")
        void blankStored() {
            Application app = appWithDatatype("dt1");
            assertThat(service.configUnchangedSinceUpload(app, "dt1", "  ")).isFalse();
        }

        @Test
        @DisplayName("hash match -> true (lite eligible)")
        void hashMatch() {
            Application app = appWithDatatype("dt1");
            String stored = service.computeHash(app, "dt1").orElseThrow();
            assertThat(service.configUnchangedSinceUpload(app, "dt1", stored)).isTrue();
        }

        @Test
        @DisplayName("hash mismatch -> false (force FULL)")
        void hashMismatch() {
            Application app = appWithDatatype("dt1");
            assertThat(service.configUnchangedSinceUpload(app, "dt1", "deadbeef"))
                    .isFalse();
        }

        @Test
        @DisplayName("datatype absent -> false (decision conservative)")
        void absentDatatype() {
            Application app = mock(Application.class);
            Configuration config = mock(Configuration.class);
            when(app.getConfiguration()).thenReturn(config);
            when(config.dataDescription()).thenReturn(new LinkedHashMap<>());
            assertThat(service.configUnchangedSinceUpload(app, "x", "deadbeef")).isFalse();
        }
    }

    private Application appWithDatatype(String name) {
        return appWithDatatypeAndTags(name, new HashSet<>());
    }

    private Application appWithDatatypeAndTags(
            String name,
            java.util.Set<fr.inra.oresing.domain.application.configuration.Tag> tags) {
        Application app = mock(Application.class);
        Configuration config = mock(Configuration.class);
        when(app.getConfiguration()).thenReturn(config);
        Map<String, StandardDataDescription> map = new LinkedHashMap<>();
        // Collections vides ( pas null ) : les getters de StandardDataDescription
        // appellent tags().stream() / componentDescriptions().values() , un null
        // ferait NPE dans Jackson .
        map.put(name, new StandardDataDescription(
                ';',
                1,
                2,
                false,
                tags,
                fr.inra.oresing.domain.application.configuration.FilterModel.NONE,
                new LinkedHashSet<>(),
                new LinkedHashMap<>(),
                null,
                null,
                new LinkedHashMap<>(),
                java.util.Collections.emptyList(),
                new TreeMap<>()
        ));
        when(config.dataDescription()).thenReturn(map);
        when(app.getName()).thenReturn("testApp");
        return app;
    }
}
