package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.chunk.ChunkMetadata;
import fr.inrae.ore.cascade.model.core.Source;
import fr.inrae.ore.cascade.model.workflow.WorkflowConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Cascade {@link Source} qui découpe un fichier CSV sans en-tête en plusieurs
 * fichiers de chunks physiques sur disque , puis émet un chunk cascade par
 * fichier. Chaque {@code Chunk<Path>} contient exactement un Path : celui
 * vers le fichier de chunk créé.
 *
 * <p>Cette source reproduit le comportement de l'ancien
 * {@code ChunkerService} du JAR file-processor , en restant compatible avec
 * {@link fr.inra.oresing.domain.data.deposit.DataImporter#doDataTreatment}
 * qui lit un fichier CSV et en produit un autre.
 *
 * <p><b>Configuration de la taille de chunk</b> : la valeur est lue depuis
 * {@link WorkflowConfig#sourceChunkSize()} via le hook
 * {@link Source#onWorkflowStart(WorkflowConfig)} appelé par l'executor cascade
 * juste avant {@link #read(String)}. Si {@code WorkflowConfig.sourceChunkSize}
 * vaut sa valeur par défaut cascade ( {@code Integer.MAX_VALUE} = "pas de
 * découpage" ) , la valeur de fallback fournie au constructeur est utilisée.
 */
public final class FileChunkSource implements Source<Path> {

    /** Valeur de fallback utilisée si la WorkflowConfig ne fournit pas de
     *  taille de chunk explicite ( ie. {@code Integer.MAX_VALUE} cascade ). */
    private static final int DEFAULT_CHUNK_SIZE_LINES = 1000;

    private final Path inputFile;
    private final Path chunksDir;
    private final String sourceName;
    private final int fallbackChunkSizeLines;

    /** Effective chunk size resolved at workflow start ; -1 jusqu'au hook. */
    private volatile int effectiveChunkSizeLines = -1;

    /**
     * Construit la source avec une taille de chunk de fallback ( utilisée
     * si la {@link WorkflowConfig} ne précise rien ).
     */
    public FileChunkSource(Path inputFile, Path chunksDir, int fallbackChunkSizeLines) {
        this.inputFile              = inputFile;
        this.chunksDir              = chunksDir;
        this.sourceName             = "FileChunkSource[" + inputFile.getFileName() + "]";
        this.fallbackChunkSizeLines = fallbackChunkSizeLines > 0
                ? fallbackChunkSizeLines
                : DEFAULT_CHUNK_SIZE_LINES;
    }

    /** Surcharge sans fallback explicite , utilise la constante par défaut. */
    public FileChunkSource(Path inputFile, Path chunksDir) {
        this(inputFile, chunksDir, DEFAULT_CHUNK_SIZE_LINES);
    }

    @Override
    public void onWorkflowStart(WorkflowConfig config) {
        // sourceChunkSize cascade défaut = Integer.MAX_VALUE ( "pas de
        // chunking" ). Dans ce cas on garde la valeur de fallback ( 1000
        // lignes ) plutôt que de produire un seul chunk gigantesque qui
        // mettrait en échec tout le bénéfice de la pipeline.
        int requested = config.sourceChunkSize();
        if (requested > 0 && requested != Integer.MAX_VALUE) {
            this.effectiveChunkSizeLines = requested;
        } else {
            this.effectiveChunkSizeLines = fallbackChunkSizeLines;
        }
    }

    @Override
    public Stream<Chunk<Path>> read(String correlationId) {
        // L'executor cascade appelle onWorkflowStart juste avant read,
        // mais on garde un fallback défensif au cas où la source serait
        // utilisée hors workflow ( tests , appel direct ).
        if (effectiveChunkSizeLines <= 0) {
            effectiveChunkSizeLines = fallbackChunkSizeLines;
        }
        try {
            Files.createDirectories(chunksDir);
            List<Chunk<Path>> chunks = preChunkFile(correlationId);
            return chunks.stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to pre-chunk input file " + inputFile, e);
        }
    }

    @Override
    public String getName() {
        return sourceName;
    }

    @Override
    public void validate() {
        if (inputFile == null || !Files.exists(inputFile)) {
            throw new IllegalStateException("Input file does not exist: " + inputFile);
        }
        if (fallbackChunkSizeLines <= 0) {
            throw new IllegalArgumentException("fallbackChunkSizeLines must be > 0");
        }
    }

    private List<Chunk<Path>> preChunkFile(String correlationId) throws IOException {
        final int chunkSize = effectiveChunkSizeLines;
        List<Chunk<Path>> chunks = new ArrayList<>();
        int chunkIndex = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
            String line;
            Path chunkPath = null;
            int linesInCurrentChunk = 0;
            BufferedWriter writer = null;

            try {
                while ((line = reader.readLine()) != null) {
                    if (writer == null) {
                        chunkPath = chunksDir.resolve(String.format("chunk_%04d.csv", chunkIndex));
                        writer = Files.newBufferedWriter(chunkPath, StandardCharsets.UTF_8);
                        linesInCurrentChunk = 0;
                    }
                    writer.write(line);
                    writer.newLine();
                    linesInCurrentChunk++;

                    if (linesInCurrentChunk >= chunkSize) {
                        writer.close();
                        writer = null;
                        chunks.add(buildChunk(chunkIndex, chunkPath, correlationId, linesInCurrentChunk));
                        chunkIndex++;
                        linesInCurrentChunk = 0;
                    }
                }

                if (writer != null) {
                    writer.close();
                    writer = null;
                    chunks.add(buildChunk(chunkIndex, chunkPath, correlationId, linesInCurrentChunk));
                }
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
        return chunks;
    }

    private Chunk<Path> buildChunk(int index, Path path, String correlationId, int linesInChunk) {
        // logicalRecordCount = vraies lignes du chunk ( pas 1 pour 1 path ).
        // Permet à MetricsChunkInterceptor de calculer recordsReceived /
        // Processed en lignes réelles , et à result.recordsProcessed() d'être
        // correct.
        ChunkMetadata metadata = new ChunkMetadata(
                correlationId, sourceName, List.of(), Instant.now(), (long) linesInChunk);
        return new Chunk<>(index, List.of(path), metadata);
    }
}
