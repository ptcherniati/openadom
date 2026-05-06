package fr.inra.oresing.domain.data.deposit.validation.transformer.data;
import com.google.common.collect.ImmutableList;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
@Tag("domain.model")
@DisplayName("Transformer data records")
class TransformerDataRecordsTest {
    @Test
    @DisplayName("RowWithReferenceDatum - record accesseurs")
    void rowWithReferenceDatum_accessors() {
        DataDatum datum = new DataDatum();
        Map<String, Map<String, Map<String, LinkedLines>>> refs = Map.of();
        RowWithReferenceDatum row = new RowWithReferenceDatum(42L, "patternCol", datum, refs);
        assertThat(row.lineNumber()).isEqualTo(42L);
        assertThat(row.patternColumnName()).isEqualTo("patternCol");
        assertThat(row.referenceDatum()).isSameAs(datum);
        assertThat(row.refsLinkedTo()).isSameAs(refs);
    }
    @Test
    @DisplayName("RowWithReferenceDatum - equals / hashCode / toString")
    void rowWithReferenceDatum_equalsHashCodeToString() {
        DataDatum datum = new DataDatum();
        Map<String, Map<String, Map<String, LinkedLines>>> refs = Map.of();
        RowWithReferenceDatum r1 = new RowWithReferenceDatum(1L, "col", datum, refs);
        RowWithReferenceDatum r2 = new RowWithReferenceDatum(1L, "col", datum, refs);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1.toString()).isNotBlank();
    }
    @Test
    @DisplayName("ReferenceDatumAfterChecking - record accesseurs")
    void referenceDatumAfterChecking_accessors() {
        DataDatum before = new DataDatum();
        DataDatum after = new DataDatum();
        Map<String, Map<String, Map<String, LinkedLines>>> refs = Map.of();
        ImmutableList<CsvRowValidationCheckResult> errors = ImmutableList.of();
        ReferenceDatumAfterChecking rec = new ReferenceDatumAfterChecking(
                10L, "patCol", before, after, refs, errors
        );
        assertThat(rec.lineNumber()).isEqualTo(10L);
        assertThat(rec.patternColumnName()).isEqualTo("patCol");
        assertThat(rec.referenceDatumBeforeChecking()).isSameAs(before);
        assertThat(rec.referenceDatumAfterChecking()).isSameAs(after);
        assertThat(rec.refsLinkedTo()).isSameAs(refs);
        assertThat(rec.errors()).isEmpty();
    }
    @Test
    @DisplayName("ReferenceDatumAfterChecking - toString ne leve pas d exception")
    void referenceDatumAfterChecking_toString() {
        DataDatum d = new DataDatum();
        ReferenceDatumAfterChecking rec = new ReferenceDatumAfterChecking(
                1L, "col", d, d, Map.of(), ImmutableList.of()
        );
        assertThat(rec.toString()).isNotBlank();
    }
}
