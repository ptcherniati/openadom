package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Sink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cascade {@link Sink} qui concatène tous les fichiers de chunks traités
 * dans un seul fichier CSV final, en respectant l'ordre des chunks.
 *
 * <p>Les chunks peuvent arriver en désordre lorsque le parallélisme est
 * activé. On stocke donc les paths reçus dans une {@link TreeMap} indexée
 * par {@code chunkIndex}, puis la concaténation finale se fait dans
 * {@link #teardown(String)}.
 *
 * <p>Le path du fichier mergé est accessible via {@link #getMergedPath()}
 * après exécution réussie du workflow. Ce path est destiné à être passé à
 * {@code DataRepository.storeAll()} pour le chargement PostgreSQL final.
 */
public final class MergingFileSink implements Sink<Path> {

    private final Path mergedPath;
    private final TreeMap<Integer, Path> chunksByIndex = new TreeMap<>();
    private final Object writeLock = new Object();

    public MergingFileSink(Path mergedPath) {
        this.mergedPath = mergedPath;
    }

    public Path getMergedPath() {
        return mergedPath;
    }

    @Override
    public void setup(String correlationId) {
        try {
            Files.createDirectories(mergedPath.getParent());
            Files.deleteIfExists(mergedPath);
            Files.createFile(mergedPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize merged output " + mergedPath, e);
        }
    }

    @Override
    public void write(Chunk<Path> chunk) {
        if (chunk.records().isEmpty()) {
            return;
        }
        Path chunkFile = chunk.records().get(0);
        synchronized (writeLock) {
            chunksByIndex.put(chunk.chunkIndex(), chunkFile);
        }
    }

    @Override
    public void teardown(String correlationId) {
        try {
            for (Map.Entry<Integer, Path> entry : chunksByIndex.entrySet()) {
                Path chunkFile = entry.getValue();
                if (Files.exists(chunkFile)) {
                    byte[] content = Files.readAllBytes(chunkFile);
                    Files.write(mergedPath, content, StandardOpenOption.APPEND);
                    Files.deleteIfExists(chunkFile);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to merge processed chunks into " + mergedPath, e);
        }
    }

    @Override
    public String getName() {
        return "MergingFileSink[" + mergedPath.getFileName() + "]";
    }
}
