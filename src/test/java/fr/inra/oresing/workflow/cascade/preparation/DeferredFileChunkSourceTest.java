package fr.inra.oresing.workflow.cascade.preparation;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Source;
import fr.inrae.ore.cascade.model.workflow.WorkflowConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link DeferredFileChunkSource} . Verify the lazy
 * resolution contract :
 * <ul>
 *   <li>name returned before onWorkflowStart matches the constructor name ;</li>
 *   <li>onWorkflowStart resolves the supplier and constructs the delegate ;</li>
 *   <li>read() fails fast if onWorkflowStart was not called ;</li>
 *   <li>onWorkflowStart fails fast if the supplier returns null
 *       ( preparator contract violation ) ;</li>
 *   <li>idempotent onWorkflowStart ( second call re-uses the delegate ) ;</li>
 *   <li>validate / estimatedRecordCount / estimatedTotalChunks degrade
 *       gracefully when called before the delegate is built .</li>
 * </ul>
 */
class DeferredFileChunkSourceTest {

    @Test
    void getName_returns_constructor_name_before_resolution() {
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:test", () -> null, Path.of("/tmp"), 1000);
        assertThat(source.getName()).isEqualTo("deferred:test");
    }

    @Test
    void read_before_onWorkflowStart_fails_fast() {
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:test", () -> Path.of("/tmp/x"), Path.of("/tmp"), 1000);
        assertThatIllegalStateException()
                .isThrownBy(() -> source.read("cid-1"))
                .withMessageContaining("onWorkflowStart");
    }

    @Test
    void null_path_from_supplier_throws_illegal_state(@TempDir Path tempDir) {
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:test", () -> null, tempDir.resolve("chunks"), 100);
        assertThatIllegalStateException()
                .isThrownBy(() -> source.onWorkflowStart(WorkflowConfig.defaults()))
                .withMessageContaining("DataPreparator did not produce a Path");
    }

    @Test
    void onWorkflowStart_resolves_supplier_and_delegate_reads_chunks(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("input.csv");
        Files.writeString(input, "1,a\n2,b\n3,c\n4,d\n5,e\n");
        AtomicReference<Path> holder = new AtomicReference<>();
        holder.set(input);

        Path chunksDir = tempDir.resolve("chunks");
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:csv", holder::get, chunksDir, 2);
        WorkflowConfig cfg = WorkflowConfig.builder().sourceChunkSize(2).build();
        source.onWorkflowStart(cfg);

        try (Stream<Chunk<Path>> stream = source.read("cid-1")) {
            List<Chunk<Path>> chunks = stream.toList();
            // 5 lines / 2 per chunk = 3 chunks ( 2 + 2 + 1 )
            assertThat(chunks).hasSize(3);
        }
    }

    @Test
    void onWorkflowStart_is_idempotent(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("input.csv");
        Files.writeString(input, "1,a\n");
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:csv", () -> input, tempDir.resolve("chunks"), 100);

        WorkflowConfig cfg2 = WorkflowConfig.builder().sourceChunkSize(2).build();
        source.onWorkflowStart(cfg2);
        // Second call must not re-create the delegate ; verifie indirectly by
        // ensuring no exception is thrown and read still works .
        source.onWorkflowStart(cfg2);
        try (Stream<Chunk<Path>> stream = source.read("cid")) {
            assertThat(stream.toList()).hasSize(1);
        }
    }

    @Test
    void validate_passes_with_valid_config_before_resolution() {
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:test", () -> null, Path.of("/tmp"), 100);
        // validate should not throw before delegate is resolved ; the
        // path-existence check is deferred to the delegate .
        source.validate();
    }

    @Test
    void validate_rejects_invalid_chunk_size() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DeferredFileChunkSource(
                        "deferred:test", () -> null, Path.of("/tmp"), 0).validate())
                .withMessageContaining("fallbackChunkSizeLines");
    }

    @Test
    void estimated_metrics_degrade_to_unknown_before_resolution() {
        DeferredFileChunkSource source = new DeferredFileChunkSource(
                "deferred:test", () -> null, Path.of("/tmp"), 100);
        assertThat(source.estimatedRecordCount()).isEqualTo(-1L);
        assertThat(source.estimatedTotalChunks()).isEmpty();
    }

    @Test
    void constructor_rejects_null_args() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DeferredFileChunkSource(null, () -> null, Path.of("/tmp"), 100));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DeferredFileChunkSource("n", null, Path.of("/tmp"), 100));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DeferredFileChunkSource("n", () -> null, null, 100));
    }

    @Test
    void implements_cascade_Source_contract() {
        // Compile-time sanity check : the type must be assignable to the
        // cascade Source<Path> SPI without cast .
        Source<Path> asGenericSource = new DeferredFileChunkSource(
                "deferred:test", () -> null, Path.of("/tmp"), 100);
        assertThat(asGenericSource.getName()).isEqualTo("deferred:test");
    }
}
