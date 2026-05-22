package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.chunk.ChunkMetadata;
import fr.inrae.ore.cascade.model.collector.CollectorContext;
import fr.inrae.ore.cascade.model.core.ChunkCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Cascade {@link ChunkCollector} qui accumule les chunk-file paths produits
 * par le stage TRANSFORM puis , a {@link #finish()} , concatene tous les
 * chunks en 1 seul fichier {@code merged.csv} et retourne un chunk unique
 * pointant vers ce fichier .
 *
 * <h2>Cycle de vie ( state machine )</h2>
 *
 * <pre>
 *   NEW  -- initialize() -->  READY  -- finish() -->  FINISHED
 * </pre>
 *
 * <p>Toute transition invalide leve {@link IllegalStateException} ; on
 * preserve ainsi un bug ( ex : finish() sans initialize() ) au lieu de le
 * masquer par un fallback silencieux . La condition prealable a un
 * fonctionnement correct est que le {@link #mergedPath} vive dans un
 * repertoire dedie au workflow ( ex : {@code processedTempDir/<corrId>} )
 * gere par {@code WorkflowTempCleanup} : aucun process externe ( systemd
 * tmpfiles , cron tmpwatch ) ne doit pouvoir le supprimer pendant
 * l'execution .
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #accept(Chunk)} est appele en parallele sur le pool collector
 * cascade ; on serialise les insertions dans la {@link TreeMap} via un
 * {@code synchronized} bref ( O(1) par chunk ) . {@link #initialize} et
 * {@link #finish} sont appeles 1 seule fois en single-thread par cascade .
 *
 * @author R.YAHIAOUI
 */
public final class MergedFileChunkCollector implements ChunkCollector<Path> {

    private static final Logger log = LoggerFactory.getLogger(MergedFileChunkCollector.class);

    private enum State { NEW, READY, FINISHED }

    private final Path mergedPath;
    private final TreeMap<Integer, Path> chunksByIndex = new TreeMap<>();
    private final Object writeLock = new Object();
    private volatile String correlationId = "unknown";
    private volatile State state = State.NEW;

    public MergedFileChunkCollector(Path mergedPath) {
        this.mergedPath = Objects.requireNonNull(mergedPath, "mergedPath cannot be null");
    }

    public Path getMergedPath() {
        return mergedPath;
    }

    @Override
    public String getName() {
        return "MergedFileChunkCollector[" + mergedPath.getFileName() + "]";
    }

    @Override
    public void initialize(CollectorContext context) {
        requireState(State.NEW, "initialize");
        if (context != null && context.correlationId() != null) {
            this.correlationId = context.correlationId();
        }
        try {
            if (mergedPath.getParent() != null) {
                Files.createDirectories(mergedPath.getParent());
            }
            Files.deleteIfExists(mergedPath);
            Files.createFile(mergedPath);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to initialize merged output " + mergedPath, e);
        }
        state = State.READY;
    }

    @Override
    public void accept(Chunk<Path> chunk) {
        if (chunk.records().isEmpty()) {
            return;
        }
        synchronized (writeLock) {
            chunksByIndex.put(chunk.chunkIndex(), chunk.records().get(0));
        }
    }

    /**
     * Concatene tous les chunk files dans {@code merged.csv} en respectant
     * l'ordre des chunkIndex ( {@link TreeMap} les retourne tries ) , puis
     * retourne 1 chunk unique pointant sur le fichier merge . Le sink
     * downstream ( {@link StoreAllPathSink} ) recevra ce chunk et lancera
     * le COPY massif vers la table finale .
     */
    @Override
    public CompletableFuture<Optional<Chunk<Path>>> finish() {
        return CompletableFuture.supplyAsync(() -> {
            // Defense en profondeur : si cascade n'a pas appele
            // {@link #initialize(CollectorContext)} ( cas observe en
            // production avec MERGE_FILE + STAGED + 0 chunk : cascade
            // skip parfois la phase COLLECTOR quand le source emit 0
            // chunk effectif ) , on auto-initialize ici plutot que
            // crasher hard . Loggue WARN pour signaler la violation
            // du contrat ChunkCollector ( init -&gt; accept -&gt; finish ) .
            if (state == State.NEW) {
                log.warn("[{}] MergedFileChunkCollector.finish ( ) called without prior initialize ( ) ; "
                                + "auto-initializing to avoid hard-crashing the workflow . "
                                + "This indicates a cascade contract violation ( missing initializeCollector call ) "
                                + "or a 0-chunk pipeline path .",
                        correlationId);
                ensureInitializedFallback();
            }
            requireState(State.READY, "finish");
            try {
                Map<Integer, Path> snapshot = snapshotChunks();
                if (snapshot.isEmpty()) {
                    state = State.FINISHED;
                    return Optional.empty();
                }
                concatenateChunks(snapshot);
                state = State.FINISHED;
                return Optional.of(buildMergedChunk());
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to merge chunks into " + mergedPath, e);
            }
        });
    }

    private Map<Integer, Path> snapshotChunks() {
        synchronized (writeLock) {
            return Map.copyOf(chunksByIndex);
        }
    }

    private void concatenateChunks(Map<Integer, Path> snapshot) throws IOException {
        for (Map.Entry<Integer, Path> entry : new TreeMap<>(snapshot).entrySet()) {
            Path chunkFile = entry.getValue();
            if (Files.exists(chunkFile)) {
                Files.write(mergedPath, Files.readAllBytes(chunkFile),
                        StandardOpenOption.APPEND);
                Files.deleteIfExists(chunkFile);
            }
        }
    }

    private Chunk<Path> buildMergedChunk() {
        ChunkMetadata md = new ChunkMetadata(
                correlationId, getName(), List.of(), Instant.now(), null);
        return new Chunk<>(0, List.of(mergedPath), md);
    }

    /**
     * Cree {@link #mergedPath} et passe l'etat a READY sans passer par
     * {@link #initialize(CollectorContext)} . Reserve au fallback
     * defensif dans {@link #finish()} ( cascade qui n'a pas appele
     * initialize ) . Logge un WARN dans tous les cas pour que la
     * violation du contrat reste visible .
     */
    private void ensureInitializedFallback() {
        try {
            if (mergedPath.getParent() != null) {
                Files.createDirectories(mergedPath.getParent());
            }
            if (!Files.exists(mergedPath)) {
                Files.createFile(mergedPath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Fallback initialize failed for " + mergedPath, e);
        }
        state = State.READY;
    }

    private void requireState(State expected, String operation) {
        if (state != expected) {
            throw new IllegalStateException(
                    "Cannot " + operation + "() in state " + state
                            + " ; expected " + expected
                            + " ( collector=" + getName() + " , correlationId=" + correlationId + " )");
        }
    }
}