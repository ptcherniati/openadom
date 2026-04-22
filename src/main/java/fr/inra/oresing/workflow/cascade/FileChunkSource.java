package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.chunk.ChunkMetadata;
import fr.inrae.ore.cascade.model.core.Source;

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
 * fichiers de chunks physiques sur disque, puis émet un chunk cascade par
 * fichier. Chaque {@code Chunk<Path>} contient exactement un Path : celui
 * vers le fichier de chunk créé.
 *
 * <p>Cette source reproduit le comportement de l'ancien
 * {@code ChunkerService} du JAR file-processor, en restant compatible avec
 * {@link fr.inra.oresing.domain.data.deposit.DataImporter#doDataTreatment}
 * qui lit un fichier CSV et en produit un autre.
 *
 * <p>Les chunks sont pré-créés lors de l'appel à {@link #read(String)}. Pour
 * des fichiers très volumineux on pourra passer à un découpage paresseux,
 * mais l'approche actuelle est identique à celle du chunker legacy.
 */
public final class FileChunkSource implements Source<Path> {

    private final Path inputFile;
    private final int chunkSizeLines;
    private final Path chunksDir;
    private final String sourceName;

    public FileChunkSource(Path inputFile, int chunkSizeLines, Path chunksDir) {
        this.inputFile      = inputFile;
        this.chunkSizeLines = chunkSizeLines;
        this.chunksDir      = chunksDir;
        this.sourceName     = "FileChunkSource[" + inputFile.getFileName() + "]";
    }

    @Override
    public Stream<Chunk<Path>> read(String correlationId) {
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
        if (chunkSizeLines <= 0) {
            throw new IllegalArgumentException("chunkSizeLines must be > 0");
        }
    }

    private List<Chunk<Path>> preChunkFile(String correlationId) throws IOException {
        List<Chunk<Path>> chunks = new ArrayList<>();
        int chunkIndex = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
            String line;
            BufferedWriter writer = null;
            Path chunkPath = null;
            int linesInCurrentChunk = 0;

            while ((line = reader.readLine()) != null) {
                if (writer == null) {
                    chunkPath = chunksDir.resolve(String.format("chunk_%04d.csv", chunkIndex));
                    writer    = Files.newBufferedWriter(chunkPath, StandardCharsets.UTF_8);
                    linesInCurrentChunk = 0;
                }
                writer.write(line);
                writer.newLine();
                linesInCurrentChunk++;

                if (linesInCurrentChunk >= chunkSizeLines) {
                    writer.close();
                    chunks.add(buildChunk(chunkIndex, chunkPath, correlationId));
                    chunkIndex++;
                    writer              = null;
                    linesInCurrentChunk = 0;
                }
            }

            if (writer != null) {
                writer.close();
                chunks.add(buildChunk(chunkIndex, chunkPath, correlationId));
            }
        }
        return chunks;
    }

    private Chunk<Path> buildChunk(int index, Path path, String correlationId) {
        ChunkMetadata metadata = new ChunkMetadata(correlationId, sourceName, List.of(), Instant.now());
        return new Chunk<>(index, List.of(path), metadata);
    }
}
