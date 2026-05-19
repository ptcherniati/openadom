package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.progress.ImportProgressReporter;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Transformation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cascade {@link Transformation} qui delegue le traitement d'un chunk
 * physique (fichier CSV) au metier existant
 * {@link DataImporter#doDataTreatment}.
 *
 * <p>Phase 1e (#62) : ne depend plus que de types backend ou cascade.
 * Tous les imports {@code fr.inra.oresing.fileprocessor.*} ont ete
 * supprimes. Les anciens objets {@code SharedContext}, {@code ChunkInfo},
 * {@code WorkflowProperties}, {@code WorkflowLifecycleManager} sont
 * remplaces par {@link ImportProperties} + {@link ImportProgressReporter}
 * + des primitives passees au DataImporter.
 */
public final class DataImporterTransformation implements Transformation<Path, Path> {

    private final DataImporter             dataImporter;
    private final ImportProperties         importProperties;
    private final ImportProgressReporter   progressReporter;
    private final Path                     processedDir;
    private final String                   correlationId;

    public DataImporterTransformation(
            DataImporter             dataImporter,
            ImportProperties         importProperties,
            ImportProgressReporter   progressReporter,
            Path                     processedDir,
            String                   correlationId) {
        this.dataImporter      = dataImporter;
        this.importProperties  = importProperties;
        this.progressReporter  = progressReporter;
        this.processedDir      = processedDir;
        this.correlationId     = correlationId;
    }

    @Override
    public Chunk<Path> transformChunk(Chunk<Path> chunk) {
        if (chunk.records().isEmpty()) {
            return chunk;
        }
        Path chunkFile = chunk.records().get(0);
        try {
            Files.createDirectories(processedDir);
            Path processedPath = processedDir.resolve(
                    String.format("processed_chunk_%s_%04d.csv", correlationId, chunk.chunkIndex()));

            AtomicInteger dataLinesProcessed   = new AtomicInteger();
            AtomicInteger successfulLinesBatch = new AtomicInteger();

            dataImporter.doDataTreatment(
                    processedPath,
                    chunkFile,
                    correlationId,
                    chunk.chunkIndex(),
                    importProperties.getChunkSizeLines(),
                    importProperties.getProgressBatchSize(),
                    dataLinesProcessed,
                    successfulLinesBatch,
                    progressReporter);

            int remainingSuccess = successfulLinesBatch.get();
            if (remainingSuccess > 0) {
                progressReporter.onLinesProcessed(correlationId, remainingSuccess);
            }

            Files.deleteIfExists(chunkFile);

            // logicalRecordCount = lignes reellement traitees pour ce chunk.
            // MetricsChunkInterceptor le lit pour cumuler le total dans
            // WorkflowResult.recordsProcessed.
            return new Chunk<>(
                    chunk.chunkIndex(),
                    List.of(processedPath),
                    chunk.metadata().withLogicalRecordCount(dataLinesProcessed.get()));

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to process chunk " + chunk.chunkIndex() + " for workflow " + correlationId, e);
        }
    }

    @Override
    public String getName() {
        return "DataImporterTransformation";
    }
}