package fr.inra.oresing.domain.data.deposit;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.context.LineContext;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Pipeline context records — accesseurs et délégation")
@Tag("domain.model")
class PipelineContextRecordsTest {

    @Test
    @DisplayName("LineContext expose le dataImporterContext fourni")
    void lineContextExposesContext() {
        AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
        LineContext lineContext = new LineContext(ctx);
        assertThat(lineContext.dataImporterContext()).isSameAs(ctx);
    }

    @Test
    @DisplayName("KeysAndReferenceDatumAfterChecking.getLineNumber() délègue à referenceDatumAfterChecking.lineNumber()")
    void keysAndReferenceGetLineNumberDelegates() {
        ReferenceDatumAfterChecking refDatum = mock(ReferenceDatumAfterChecking.class);
        when(refDatum.lineNumber()).thenReturn(42L);

        Ltree naturalKey = Ltree.fromSqlWithoutCheck("root");
        Ltree hierarchicalKey = Ltree.fromSqlWithoutCheck("root");
        KeysAndReferenceDatumAfterChecking entry = new KeysAndReferenceDatumAfterChecking(
                refDatum, naturalKey, hierarchicalKey, "col");

        assertThat(entry.getLineNumber()).isEqualTo(42L);
        assertThat(entry.referenceDatumAfterChecking()).isSameAs(refDatum);
        assertThat(entry.naturalKey()).isEqualTo(naturalKey);
        assertThat(entry.hierarchicalKey()).isEqualTo(hierarchicalKey);
        assertThat(entry.patternColumnName()).isEqualTo("col");
    }

    @Test
    @DisplayName("RowWithReferenceDatum expose tous ses composants via les accesseurs du record")
    void rowWithReferenceDatumAccessors() {
        DataDatum datum = new DataDatum();
        Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo = Map.of();
        RowWithReferenceDatum row = new RowWithReferenceDatum(7L, "patternCol", datum, refsLinkedTo);

        assertThat(row.lineNumber()).isEqualTo(7L);
        assertThat(row.patternColumnName()).isEqualTo("patternCol");
        assertThat(row.referenceDatum()).isSameAs(datum);
        assertThat(row.refsLinkedTo()).isSameAs(refsLinkedTo);
    }

    @Test
    @DisplayName("ReferenceDatumAfterChecking construit directement expose lineNumber()")
    void referenceDatumAfterCheckingDirectConstruction() {
        DataDatum before = new DataDatum();
        DataDatum after = new DataDatum();
        ReferenceDatumAfterChecking entry = new ReferenceDatumAfterChecking(
                99L, "col", before, after, Map.of(), List.of());

        assertThat(entry.lineNumber()).isEqualTo(99L);
        assertThat(entry.patternColumnName()).isEqualTo("col");
        assertThat(entry.referenceDatumBeforeChecking()).isSameAs(before);
        assertThat(entry.referenceDatumAfterChecking()).isSameAs(after);
        assertThat(entry.errors()).isEmpty();
    }
}
