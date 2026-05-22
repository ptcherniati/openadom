package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.chunk.ChunkMetadata;
import fr.inrae.ore.cascade.model.collector.CollectorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour {@link MergedFileChunkCollector} . Couvre :
 *
 * <ul>
 *   <li>concatenation des chunk-files dans l'ordre des chunkIndex
 *       ( meme quand accept() est appele en desordre )</li>
 *   <li>cleanup automatique des chunk-files apres merge</li>
 *   <li>cas vide ( aucun chunk recu ) -&gt; finish() retourne empty</li>
 *   <li>finish() retourne un chunk unique avec la merged.csv path</li>
 * </ul>
 */
@DisplayName("MergedFileChunkCollector")
class MergedFileChunkCollectorTest {

    @TempDir
    Path tempDir;

    private Path mergedPath;
    private MergedFileChunkCollector collector;

    @BeforeEach
    void setUp() {
        mergedPath = tempDir.resolve("merged.csv");
        collector = new MergedFileChunkCollector(mergedPath);
        collector.initialize(new CollectorContext("test-cid", -1, null, null, null, tempDir));
    }

    @Test
    @DisplayName("getName ( ) reflete le nom du fichier merge")
    void name_includes_merged_filename() {
        assertThat(collector.getName()).contains("merged.csv");
    }

    @Test
    @DisplayName("initialize ( ) cree un fichier merged.csv vide")
    void initialize_creates_empty_merged_file() {
        assertThat(Files.exists(mergedPath)).isTrue();
        assertThat(mergedPath).hasSize(0L);
    }

    @Test
    @DisplayName("finish ( ) retourne empty quand aucun chunk recu")
    void finish_empty_returns_optional_empty() throws Exception {
        Optional<Chunk<Path>> out = collector.finish().get();
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("finish ( ) concatene les chunks dans l'ordre des chunkIndex")
    void finish_concatenates_chunks_in_index_order() throws Exception {
        Path c0 = writeChunkFile("chunk-0.csv", "alpha\n");
        Path c1 = writeChunkFile("chunk-1.csv", "beta\n");
        Path c2 = writeChunkFile("chunk-2.csv", "gamma\n");

        // Accept en desordre exprès pour verifier le tri par chunkIndex .
        collector.accept(chunkOf(2, c2));
        collector.accept(chunkOf(0, c0));
        collector.accept(chunkOf(1, c1));

        Optional<Chunk<Path>> out = collector.finish().get();

        assertThat(out).isPresent();
        assertThat(out.get().records()).containsExactly(mergedPath);
        assertThat(Files.readString(mergedPath, StandardCharsets.UTF_8))
                .isEqualTo("alpha\nbeta\ngamma\n");
    }

    @Test
    @DisplayName("finish ( ) supprime les chunk-files apres merge")
    void finish_deletes_chunk_files() throws Exception {
        Path c0 = writeChunkFile("chunk-a.csv", "hello\n");
        collector.accept(chunkOf(0, c0));

        collector.finish().get();

        assertThat(Files.exists(c0)).isFalse();
        assertThat(Files.exists(mergedPath)).isTrue();
    }

    @Test
    @DisplayName("accept ( ) ignore silencieusement un chunk vide")
    void accept_empty_chunk_ignored() throws Exception {
        ChunkMetadata md = new ChunkMetadata("test-cid", "src", List.of(), Instant.now(), null);
        Chunk<Path> empty = new Chunk<>(0, List.of(), md);
        collector.accept(empty);

        Optional<Chunk<Path>> out = collector.finish().get();
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("finish ( ) retourne un chunk avec la merged.csv path et chunkIndex 0")
    void finish_returns_single_merged_chunk() throws Exception {
        Path c0 = writeChunkFile("c0.csv", "x\n");
        collector.accept(chunkOf(0, c0));

        Chunk<Path> merged = collector.finish().get().orElseThrow();

        assertThat(merged.chunkIndex()).isZero();
        assertThat(merged.records()).containsExactly(mergedPath);
    }

    @Test
    @DisplayName("finish ( ) sans initialize ( ) auto-recover via fallback ( cascade contract violation logged )")
    void finish_without_initialize_auto_recovers() throws Exception {
        Path other = tempDir.resolve("merged-no-init.csv");
        MergedFileChunkCollector orphan = new MergedFileChunkCollector(other);

        // 0 chunk accepted + finish() -> fallback init + Optional.empty
        Optional<Chunk<Path>> result = orphan.finish().get();
        assertThat(result).isEmpty();
        // mergedPath created by fallback ( may be deleted later but the
        // collector did not crash ) .
    }

    @Test
    @DisplayName("finish ( ) sans initialize ( ) avec chunks accept-es : fallback init + concat")
    void finish_without_initialize_with_chunks_succeeds() throws Exception {
        Path other = tempDir.resolve("merged-no-init-with-chunks.csv");
        MergedFileChunkCollector orphan = new MergedFileChunkCollector(other);

        Path c0 = writeChunkFile("c0-fallback.csv", "fallback-data\n");
        orphan.accept(chunkOf(0, c0));

        Chunk<Path> merged = orphan.finish().get().orElseThrow();
        assertThat(Files.exists(other)).isTrue();
        assertThat(Files.readString(other, StandardCharsets.UTF_8)).isEqualTo("fallback-data\n");
        assertThat(merged.records()).containsExactly(other);
    }

    @Test
    @DisplayName("initialize ( ) appele 2 fois leve IllegalStateException")
    void double_initialize_throws_illegal_state() {
        // collector deja initialize via @BeforeEach -> 2eme appel doit refuser .
        assertThatThrownBy(() ->
                collector.initialize(new CollectorContext("cid", -1, null, null, null, tempDir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected NEW");
    }

    @Test
    @DisplayName("finish ( ) appele 2 fois leve IllegalStateException")
    void double_finish_throws_illegal_state() throws Exception {
        Path c0 = writeChunkFile("c0.csv", "x\n");
        collector.accept(chunkOf(0, c0));
        collector.finish().get();

        assertThatThrownBy(() -> collector.finish().get())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected READY")
                .hasMessageContaining("state FINISHED");
    }

    @Test
    @DisplayName("initialize ( ) cree les repertoires parents manquants ( workflow-scoped path )")
    void initialize_creates_parent_directories() {
        Path nested = tempDir.resolve("a/b/c/merged.csv");
        MergedFileChunkCollector c = new MergedFileChunkCollector(nested);

        c.initialize(new CollectorContext("cid", -1, null, null, null, tempDir));

        assertThat(Files.exists(nested)).isTrue();
        assertThat(Files.isDirectory(nested.getParent())).isTrue();
    }

    @Test
    @DisplayName("initialize ( ) ecrase un merged.csv preexistant ( reprise apres echec )")
    void initialize_replaces_existing_merged_file() throws Exception {
        Path target = tempDir.resolve("preexisting/merged.csv");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "stale-content-from-previous-run", StandardCharsets.UTF_8);

        MergedFileChunkCollector c = new MergedFileChunkCollector(target);
        c.initialize(new CollectorContext("cid", -1, null, null, null, tempDir));

        assertThat(target).hasSize(0L);
    }

    private Path writeChunkFile(String name, String content) throws Exception {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private Chunk<Path> chunkOf(int index, Path path) {
        ChunkMetadata md = new ChunkMetadata(
                UUID.randomUUID().toString(), "test-source", List.of(),
                Instant.now(), null);
        return new Chunk<>(index, List.of(path), md);
    }
}