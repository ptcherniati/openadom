package fr.inra.oresing.domain;

// BinaryFileInfos est dans le même package fr.inra.oresing.domain — pas d'import nécessaire
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs du record {@link BinaryFileInfos} — aucune dépendance Spring ni base.
 */
@DisplayName("BinaryFileInfos record")
@Tag("domain.model")
class BinaryFileInfosTest {

    private static BinaryFileDataset dataset(String comment) {
        BinaryFileDataset ds = new BinaryFileDataset();
        ds.setComment(comment);
        return ds;
    }

    // -------------------------------------------------------------------------
    // Constructeur compact (depuis BinaryFileDataset)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Constructeur compact BinaryFileInfos(BinaryFileDataset)")
    class CompactConstructorTest {

        @Test
        @DisplayName("published=false, champs user/date à null")
        void defaultsAreUnpublished() {
            BinaryFileDataset ds = dataset("my comment");
            BinaryFileInfos infos = new BinaryFileInfos(ds);

            assertThat(infos.published()).isFalse();
            assertThat(infos.publisheduser()).isNull();
            assertThat(infos.publisheddate()).isNull();
            assertThat(infos.createuser()).isNull();
            assertThat(infos.createdate()).isNull();
            assertThat(infos.comment()).isNull();
            assertThat(infos.binaryFiledataset()).isSameAs(ds);
        }

        @Test
        @DisplayName("Dataset null accepté (valeurs nulles en cascade)")
        void nullDatasetAccepted() {
            BinaryFileInfos infos = new BinaryFileInfos((BinaryFileDataset) null);
            assertThat(infos.published()).isFalse();
            assertThat(infos.binaryFiledataset()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // forPublish — factory method statique
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("forPublish()")
    class ForPublishTest {

        @Test
        @DisplayName("Crée un enregistrement publié avec les bons champs")
        void createsPublishedRecord() {
            UUID userId = UUID.randomUUID();
            BinaryFileDataset ds = dataset("note");

            BinaryFileInfos infos = BinaryFileInfos.forPublish(true, userId, "2024-01-15", ds);

            assertThat(infos.published()).isTrue();
            assertThat(infos.publisheduser()).isEqualTo(userId);
            assertThat(infos.publisheddate()).isEqualTo("2024-01-15");
            assertThat(infos.createuser()).isNull();
            assertThat(infos.createdate()).isNull();
            assertThat(infos.comment()).isEqualTo("note");
            assertThat(infos.binaryFiledataset()).isSameAs(ds);
        }

        @Test
        @DisplayName("Dataset null → commentaire vide (pas de NPE)")
        void nullDatasetYieldsEmptyComment() {
            BinaryFileInfos infos = BinaryFileInfos.forPublish(false, null, null, null);
            assertThat(infos.comment()).isEmpty();
            assertThat(infos.binaryFiledataset()).isNull();
        }

        @Test
        @DisplayName("Dataset sans commentaire → commentaire vide")
        void datasetWithNullCommentYieldsEmptyComment() {
            BinaryFileDataset ds = new BinaryFileDataset(); // comment = null
            BinaryFileInfos infos = BinaryFileInfos.forPublish(true, UUID.randomUUID(), "2024-06", ds);
            assertThat(infos.comment()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // markAsPublished — mutation immuable
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("markAsPublished()")
    class MarkAsPublishedTest {

        @Test
        @DisplayName("Passage à published=true conserve publisheduser et publisheddate")
        void markTruePreservesPublishedFields() {
            UUID userId = UUID.randomUUID();
            BinaryFileInfos original = BinaryFileInfos.forPublish(true, userId, "2024-03-10", dataset("x"));

            BinaryFileInfos marked = original.markAsPublished(true);

            assertThat(marked.published()).isTrue();
            assertThat(marked.publisheduser()).isEqualTo(userId);
            assertThat(marked.publisheddate()).isEqualTo("2024-03-10");
        }

        @Test
        @DisplayName("Passage à published=false met publisheduser et publisheddate à null")
        void markFalseClearsPublishedFields() {
            UUID userId = UUID.randomUUID();
            BinaryFileInfos original = BinaryFileInfos.forPublish(true, userId, "2024-03-10", dataset("x"));

            BinaryFileInfos marked = original.markAsPublished(false);

            assertThat(marked.published()).isFalse();
            assertThat(marked.publisheduser()).isNull();
            assertThat(marked.publisheddate()).isNull();
        }

        @Test
        @DisplayName("L'original est inchangé (immuabilité du record)")
        void originalUnchanged() {
            UUID userId = UUID.randomUUID();
            BinaryFileInfos original = BinaryFileInfos.forPublish(true, userId, "2024-03-10", dataset("x"));
            original.markAsPublished(false);

            assertThat(original.published()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // withBinaryFileDataset — remplacement du dataset
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("withBinaryFileDataset()")
    class WithBinaryFileDatasetTest {

        @Test
        @DisplayName("Remplace uniquement le dataset, les autres champs sont conservés")
        void replacesOnlyDataset() {
            UUID userId = UUID.randomUUID();
            BinaryFileDataset ds1 = dataset("old");
            BinaryFileInfos original = BinaryFileInfos.forPublish(true, userId, "2024-01-01", ds1);

            BinaryFileDataset ds2 = dataset("new");
            BinaryFileInfos updated = original.withBinaryFileDataset(ds2);

            assertThat(updated.binaryFiledataset()).isSameAs(ds2);
            assertThat(updated.published()).isTrue();
            assertThat(updated.publisheduser()).isEqualTo(userId);
            assertThat(updated.publisheddate()).isEqualTo("2024-01-01");
        }

        @Test
        @DisplayName("Remplacement par null ne lève pas d'exception")
        void replaceWithNull() {
            BinaryFileInfos original = new BinaryFileInfos(dataset("x"));
            BinaryFileInfos updated = original.withBinaryFileDataset(null);
            assertThat(updated.binaryFiledataset()).isNull();
        }
    }
}