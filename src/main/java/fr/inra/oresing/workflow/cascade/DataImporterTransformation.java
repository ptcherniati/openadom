package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.fileprocessor.workflow.config.WorkflowProperties;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowLifecycleManager;
import fr.inra.oresing.fileprocessor.workflow.entity.ChunkInfo;
import fr.inra.oresing.fileprocessor.workflow.entity.context.SharedContext;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Transformation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cascade {@link Transformation} qui délègue le traitement d'un chunk
 * physique (fichier CSV) au métier existant
 * {@link DataImporter#doDataTreatment}.
 *
 * <p>Le couplage aux types file-processor ({@link ChunkInfo},
 * {@link SharedContext}, {@link WorkflowProperties},
 * {@link WorkflowLifecycleManager}) est conservé dans cette classe pour
 * éviter toute modification de {@link DataImporter} : cette classe est le
 * SEUL pont entre cascade et l'API file-processor durant la phase 1a.
 *
 * <p>Un {@link SharedContext} est partagé entre tous les chunks d'un même
 * workflow : il porte le compteur d'erreurs et le plafond
 * {@code maxErrorsThreshold} qui permet à DataImporter d'arrêter l'import
 * lorsque trop d'erreurs s'accumulent (via {@code takeWhile}).
 */
public final class DataImporterTransformation implements Transformation<Path, Path> {

    private final DataImporter              dataImporter;
    private final WorkflowProperties        workflowProperties;
    private final WorkflowLifecycleManager  lifecycleManager;
    private final SharedContext             sharedContext;
    private final Path                      processedDir;
    private final String                    correlationId;
    private final String                    userId;

    public DataImporterTransformation(
            DataImporter              dataImporter,
            WorkflowProperties        workflowProperties,
            WorkflowLifecycleManager  lifecycleManager,
            SharedContext             sharedContext,
            Path                      processedDir,
            String                    correlationId,
            String                    userId) {
        this.dataImporter       = dataImporter;
        this.workflowProperties = workflowProperties;
        this.lifecycleManager   = lifecycleManager;
        this.sharedContext      = sharedContext;
        this.processedDir       = processedDir;
        this.correlationId      = correlationId;
        this.userId             = userId;
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

            ChunkInfo chunkInfo = ChunkInfo.builder()
                    .correlationId(correlationId)
                    .userId        (userId)
                    .chunkNumber   (chunk.chunkIndex())
                    .chunkPath     (chunkFile)
                    .build();

            AtomicInteger dataLinesProcessed  = new AtomicInteger();
            AtomicInteger successfulLinesBatch = new AtomicInteger();

            dataImporter.doDataTreatment(
                    processedPath,
                    sharedContext,
                    chunkInfo,
                    dataLinesProcessed,
                    successfulLinesBatch,
                    workflowProperties,
                    lifecycleManager);

            int remainingSuccess = successfulLinesBatch.get();
            if (remainingSuccess > 0) {
                sharedContext.incrementProcessedLines(remainingSuccess);
                lifecycleManager.incrementProcessedLines(correlationId, remainingSuccess);
            }

            Files.deleteIfExists(chunkFile);

            return new Chunk<>(chunk.chunkIndex(), List.of(processedPath), chunk.metadata());

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
