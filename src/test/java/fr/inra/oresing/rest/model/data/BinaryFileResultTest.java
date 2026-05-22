package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link BinaryFileResult} : verifie que {@code hasLinks} est
 * correctement source-of-truth ( explicite dans la nouvelle factory ,
 * derive de {@code referencedFiles} dans la factory backward-compat ).
 */
@Tag("unit")
class BinaryFileResultTest {

    @Test
    void of_explicit_hasLinks_true_isPreserved() {
        BinaryFile binaryFile = newBinaryFile();
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, true, null);
        assertTrue(result.hasLinks());
        assertNull(result.referencedFiles());
    }

    @Test
    void of_explicit_hasLinks_false_isPreserved() {
        BinaryFile binaryFile = newBinaryFile();
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, false, null);
        assertFalse(result.hasLinks());
    }

    @Test
    void of_legacyFactory_derivesHasLinks_fromNonEmptyArray() {
        BinaryFile binaryFile = newBinaryFile();
        ReferencedBinaryFiles ref = new ReferencedBinaryFiles(
                UUID.randomUUID(), "dt",
                Map.of("targetType", List.of(UUID.randomUUID())));
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, List.of(ref));
        assertTrue(result.hasLinks());
    }

    @Test
    void of_legacyFactory_emptyArrayMeansNoLinks() {
        BinaryFile binaryFile = newBinaryFile();
        ReferencedBinaryFiles ref = new ReferencedBinaryFiles(
                UUID.randomUUID(), "dt",
                Map.of("targetType", List.of()));
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, List.of(ref));
        assertFalse(result.hasLinks());
    }

    @Test
    void of_legacyFactory_nullList_meansNoLinks() {
        BinaryFile binaryFile = newBinaryFile();
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, null);
        assertFalse(result.hasLinks());
    }

    @Test
    void of_legacyFactory_emptyList_meansNoLinks() {
        BinaryFile binaryFile = newBinaryFile();
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, List.of());
        assertFalse(result.hasLinks());
    }

    @Test
    void of_legacyFactory_nullMap_meansNoLinks() {
        BinaryFile binaryFile = newBinaryFile();
        ReferencedBinaryFiles ref = new ReferencedBinaryFiles(UUID.randomUUID(), "dt", null);
        BinaryFileResult result = BinaryFileResult.of(binaryFile, null, null, List.of(ref));
        assertFalse(result.hasLinks());
    }

    @Test
    void of_nullParams_returnsDefaultInfosWithPublishedFalse() {
        // BinaryFile sans params (cas legacy ou import partiel)
        BinaryFile bf = new BinaryFile();
        bf.setId(UUID.randomUUID());
        bf.setName("orphan.csv");
        bf.setSize(0L);
        // params intentionnellement null

        BinaryFileResult result = BinaryFileResult.of(bf, null, null, false, null);

        assertNotNull(result.params(), "params ne doit pas être null");
        assertFalse(result.params().published(), "published doit être false quand params est null");
        assertNull(result.params().binaryFileDataset());
    }

    private static BinaryFile newBinaryFile() {
        BinaryFile bf = new BinaryFile();
        bf.setId(UUID.randomUUID());
        bf.setName("file.csv");
        bf.setComment("");
        bf.setSize(0L);
        BinaryFileDataset dataset = new BinaryFileDataset();
        dataset.setDatatype("dt");
        bf.setParams(new BinaryFileInfos(false, null, null, null, null, null, dataset));
        return bf;
    }
}