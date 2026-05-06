package fr.inra.oresing;

import fr.inra.oresing.persistence.ColumnDistinctValues;
import fr.inra.oresing.workflow.cascade.ExtractionRateLimitExceededException;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour des records et exceptions simples du domaine.
 */
@Tag("domain.model")
@DisplayName("Domain records – ColumnDistinctValues, ReferencedBinaryFiles, ExtractionRateLimitExceededException")
class DomainRecordsTest {

    // ---- ColumnDistinctValues ----

    @Test
    @DisplayName("ColumnDistinctValues – accesseurs et constante DISTINCT_VALUES_LIMIT")
    void columnDistinctValues() {
        List<String> values = List.of("a", "b", "c");
        ColumnDistinctValues cdv = new ColumnDistinctValues("myKey", values, false, true);
        assertThat(cdv.componentKey()).isEqualTo("myKey");
        assertThat(cdv.values()).containsExactly("a", "b", "c");
        assertThat(cdv.truncated()).isFalse();
        assertThat(cdv.hasEmpty()).isTrue();
        assertThat(ColumnDistinctValues.DISTINCT_VALUES_LIMIT).isEqualTo(10_000);
    }

    @Test
    @DisplayName("ColumnDistinctValues – truncated=true, values vide")
    void columnDistinctValuesTruncated() {
        ColumnDistinctValues cdv = new ColumnDistinctValues("col", List.of(), true, false);
        assertThat(cdv.truncated()).isTrue();
        assertThat(cdv.values()).isEmpty();
        assertThat(cdv.hasEmpty()).isFalse();
    }

    // ---- ReferencedBinaryFiles ----

    @Test
    @DisplayName("ReferencedBinaryFiles – accesseurs du record")
    void referencedBinaryFiles() {
        UUID fileId = UUID.randomUUID();
        Map<String, List<UUID>> refs = Map.of("refType1", List.of(UUID.randomUUID()));
        ReferencedBinaryFiles rbf = new ReferencedBinaryFiles(fileId, "myData", refs);
        assertThat(rbf.binaryFileId()).isEqualTo(fileId);
        assertThat(rbf.dataType()).isEqualTo("myData");
        assertThat(rbf.referencedBinaryFileIdsByReferencetype()).containsKey("refType1");
    }

    // ---- ExtractionRateLimitExceededException ----

    @Test
    @DisplayName("ExtractionRateLimitExceededException – message contient userId et quotas")
    void extractionRateLimitExceededException() {
        ExtractionRateLimitExceededException ex =
                new ExtractionRateLimitExceededException("user42", 3, 2);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("user42");
        assertThat(ex.getMessage()).contains("3");
        assertThat(ex.getMessage()).contains("2");
    }
}