package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.chunk.ChunkMetadata;
import fr.inrae.ore.cascade.model.collector.CollectorContext;
import fr.inrae.ore.cascade.model.core.ChunkCollector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Cascade {@link ChunkCollector} qui accumule les chunk-file paths produits
 * par le stage TRANSFORM puis , a {@link #finish()} , concatene tous les
 * chunks en 1 seul fichier {@code merged.csv} et retourne un chunk unique
 * pointant vers ce fichier .
 *
 * <p>Ce collector remplace l'ancien {@code MergingFileSink} qui violait le
 * contrat semantique de {@code Sink<T>} ( il ne persistait rien dans
 * {@code write} - juste un {@code map.put} - le vrai cout etant differe au
 * {@code teardown} ) . En refactorant en Collector :
 *
 * <ul>
 *   <li><b>UI coherent</b> : le compteur {@code &times; N} sur la ligne
 *       COLLECTOR reflete N chunks accumules ( vrai travail visible ) ; la
 *       ligne SINK montre 1 chunk = 1 ecriture DB reelle ( pas de
 *       compteur trompeur a 222 path-registrations ) .</li>
 *   <li><b>Semantique cascade respectee</b> : Sink = persiste chunk-par-
 *       chunk ; Collector = accumule + post-process . MERGE_FILE est par
 *       essence un Collector ( accumule N chunks puis 1 sortie consolidee ) .</li>
 *   <li><b>Code maintenable</b> : la concatenation des chunks fait partie
 *       integrante du pipeline cascade ( pas de step post-cascade en plus
 *       dans openADOM ) ; le pipeline se lit naturellement
 *       {@code source -> transform -> collect -> sink} .</li>
 *   <li><b>Testable</b> : un seul comportement isole ( prendre N path -&gt;
 *       produire 1 file ) que l'on couvre avec un test unitaire dedie sans
 *       monter de cascade complete .</li>
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #accept(Chunk)} est appele en parallele sur le pool collector
 * cascade ; on serialise les insertions dans la {@link TreeMap} via un
 * {@code synchronized} bref ( O(1) par chunk ) . {@link #finish()} est
 * appele 1 seule fois en single-thread par cascade .
 *
 * @author R.YAHIAOUI
 */
public final class MergedFileChunkCollector implements ChunkCollector<Path> {

    private final Path mergedPath;
    private final TreeMap<Integer, Path> chunksByIndex = new TreeMap<>();
    private final Object writeLock = new Object();
    private volatile String correlationId = "unknown";

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
        if (context != null && context.correlationId() != null) {
            this.correlationId = context.correlationId();
        }
        try {
            Files.createDirectories(mergedPath.getParent());
            Files.deleteIfExists(mergedPath);
            Files.createFile(mergedPath);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to initialize merged output " + mergedPath, e);
        }
    }

    @Override
    public void accept(Chunk<Path> chunk) {
        if (chunk.records().isEmpty()) {
            return;
        }
        Path chunkFile = chunk.records().get(0);
        synchronized (writeLock) {
            chunksByIndex.put(chunk.chunkIndex(), chunkFile);
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
            try {
                Map<Integer, Path> snapshot;
                synchronized (writeLock) {
                    snapshot = Map.copyOf(chunksByIndex);
                }
                if (snapshot.isEmpty()) {
                    return Optional.empty();
                }
                for (Map.Entry<Integer, Path> entry : new TreeMap<>(snapshot).entrySet()) {
                    Path chunkFile = entry.getValue();
                    if (Files.exists(chunkFile)) {
                        byte[] content = Files.readAllBytes(chunkFile);
                        Files.write(mergedPath, content, StandardOpenOption.APPEND);
                        Files.deleteIfExists(chunkFile);
                    }
                }
                ChunkMetadata md = new ChunkMetadata(
                        correlationId,
                        getName(),
                        List.of(),
                        Instant.now(),
                        null);
                Chunk<Path> mergedChunk = new Chunk<>(0, List.of(mergedPath), md);
                return Optional.of(mergedChunk);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to merge chunks into " + mergedPath, e);
            }
        });
    }
}
