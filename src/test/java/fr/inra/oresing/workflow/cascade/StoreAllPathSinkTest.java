package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.chunk.ChunkMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link StoreAllPathSink} . Couvre le contrat
 * d'instrumentation introduit pour fixer l'overcount apparent dans
 * {@code IntegrityService} sur le chemin MERGE_FILE :
 *
 * <ul>
 *   <li>{@code getRowsWritten()} reflete fidelement le rowcount retourne
 *       par {@link DataRepository#storeAll(Path)} ( un seul write ) .</li>
 *   <li>Les writes successifs cumulent ( idempotent vis-a-vis du
 *       compteur ) .</li>
 *   <li>Un chunk vide ne touche pas le repository ni le compteur .</li>
 *   <li>Une exception du repository propage et le compteur n'est PAS
 *       incremente ( le sink ne ment pas ) .</li>
 * </ul>
 */
@Tag("domain.model")
@DisplayName("StoreAllPathSink ( rowcount instrumentation )")
class StoreAllPathSinkTest {

    private DataRepository repo;
    private StoreAllPathSink sink;

    @BeforeEach
    void setUp() {
        repo = mock(DataRepository.class);
        sink = new StoreAllPathSink(repo);
    }

    @Test
    @DisplayName("getRowsWritten initialement zero")
    void initial_count_is_zero() {
        assertThat(sink.getRowsWritten()).isZero();
    }

    @Test
    @DisplayName("write d'un chunk avec path declenche storeAll et capture le rowcount")
    void single_write_captures_rowcount() {
        Path merged = Path.of("/tmp/merged.csv");
        when(repo.storeAll(eq(merged), any(), any())).thenReturn(274_706L);

        sink.write(chunkOf(0, merged));

        verify(repo).storeAll(eq(merged), any(), any());
        assertThat(sink.getRowsWritten()).isEqualTo(274_706L);
    }

    @Test
    @DisplayName("writes successifs cumulent le rowcount")
    void successive_writes_accumulate() {
        Path a = Path.of("/tmp/a.csv");
        Path b = Path.of("/tmp/b.csv");
        when(repo.storeAll(eq(a), any(), any())).thenReturn(1_000L);
        when(repo.storeAll(eq(b), any(), any())).thenReturn(2_500L);

        sink.write(chunkOf(0, a));
        sink.write(chunkOf(1, b));

        assertThat(sink.getRowsWritten()).isEqualTo(3_500L);
    }

    @Test
    @DisplayName("chunk vide est ignore et ne touche ni repo ni compteur")
    void empty_chunk_is_noop() {
        sink.write(new Chunk<>(0, List.<Path>of(), metadata()));

        verifyNoInteractions(repo);
        assertThat(sink.getRowsWritten()).isZero();
    }

    @Test
    @DisplayName("storeAll qui leve une exception propage et ne corrompt pas le compteur")
    void storeAll_exception_does_not_increment() {
        Path bad = Path.of("/tmp/bad.csv");
        when(repo.storeAll(eq(bad), any(), any())).thenThrow(new RuntimeException("DB down"));
        var chunk = chunkOf(0, bad);
        assertThatThrownBy(() -> sink.write(chunk))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
        assertThat(sink.getRowsWritten()).isZero();
    }

    @Test
    @DisplayName("getName retourne l'identifiant attendu par cascade")
    void getName_is_stable() {
        assertThat(sink.getName()).isEqualTo("StoreAllPathSink");
    }

    @Test
    @DisplayName("setup reset le compteur ; correct si cascade reuse l'instance entre workflows")
    void setup_resets_counter() {
        Path a = Path.of("/tmp/a.csv");
        when(repo.storeAll(eq(a), any(), any())).thenReturn(1_000L);
        sink.write(chunkOf(0, a));
        assertThat(sink.getRowsWritten()).isEqualTo(1_000L);

        sink.setup("workflow-2");

        assertThat(sink.getRowsWritten())
                .as("compteur doit etre remis a zero pour le nouveau workflow")
                .isZero();
    }

    @Test
    @DisplayName("RowCountingSink contract : getRowsWritten polymorphique via interface")
    void implements_row_counting_sink_marker() {
        assertThat((Object) sink).isInstanceOf(RowCountingSink.class);
        RowCountingSink counting = sink;
        assertThat(counting.getRowsWritten()).isZero();
    }

    // ─── deferToCaller mode ───────────────────────────────────────────────────

    @Test
    @DisplayName("deferToCaller mode : write capture le path sans appeler storeAll")
    void deferredMode_write_capturesPath_noStoreAll() {
        StoreAllPathSink deferred = new StoreAllPathSink(repo, null, true);
        Path merged = Path.of("/tmp/deferred.csv");

        deferred.write(chunkOf(0, merged));

        verifyNoInteractions(repo);
        assertThat(deferred.getRowsWritten()).isZero();
    }

    @Test
    @DisplayName("takeDeferredMergedPath retourne le path capté puis le reset (consume-once)")
    void deferredMode_takeDeferredPath_consumesOnce() {
        StoreAllPathSink deferred = new StoreAllPathSink(repo, null, true);
        Path merged = Path.of("/tmp/deferred2.csv");
        deferred.write(chunkOf(0, merged));

        java.util.Optional<Path> first  = deferred.takeDeferredMergedPath();
        java.util.Optional<Path> second = deferred.takeDeferredMergedPath();

        assertThat(first).contains(merged);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("takeDeferredMergedPath retourne empty si aucun write en deferToCaller mode")
    void deferredMode_takeEmpty() {
        StoreAllPathSink deferred = new StoreAllPathSink(repo, null, true);
        assertThat(deferred.takeDeferredMergedPath()).isEmpty();
    }

    @Test
    @DisplayName("recordDeferredRowsWritten met à jour getRowsWritten")
    void recordDeferredRowsWritten_setsCount() {
        StoreAllPathSink deferred = new StoreAllPathSink(repo, null, true);
        deferred.recordDeferredRowsWritten(9_876L);
        assertThat(deferred.getRowsWritten()).isEqualTo(9_876L);
    }

    @Test
    @DisplayName("setup avec correlationId null n'échoue pas (parseUuid null-safe)")
    void setup_nullCorrelationId_doesNotThrow() {
        sink.setup(null);
        assertThat(sink.getRowsWritten()).isZero();
    }

    @Test
    @DisplayName("setup avec correlationId invalide n'échoue pas (parseUuid IAE caught)")
    void setup_invalidUuid_doesNotThrow() {
        sink.setup("not-a-uuid");
        assertThat(sink.getRowsWritten()).isZero();
    }

    @Test
    @DisplayName("write avec registry non-null publie le rowcount via addFinalRows")
    void write_withRegistry_publishesRowCount() {
        fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry registry =
                new fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry();
        java.util.UUID cid = java.util.UUID.randomUUID();

        StoreAllPathSink sinkWithRegistry = new StoreAllPathSink(repo, registry, false);
        sinkWithRegistry.setup(cid.toString());

        Path merged = Path.of("/tmp/registry-write.csv");
        when(repo.storeAll(eq(merged), any(), any())).thenReturn(42L);

        sinkWithRegistry.write(chunkOf(0, merged));

        assertThat(sinkWithRegistry.getRowsWritten()).isEqualTo(42L);
    }

    // ---- helpers --------------------------------------------------------

    private static Chunk<Path> chunkOf(int idx, Path path) {
        return new Chunk<>(idx, List.of(path), metadata());
    }

    private static ChunkMetadata metadata() {
        return new ChunkMetadata("test-corr", "merged.csv", List.of(), Instant.now(), null);
    }
}