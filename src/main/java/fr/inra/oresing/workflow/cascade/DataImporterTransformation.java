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
import java.nio.file.StandardOpenOption;
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
    /**
     * Publish FAST path : si non-null , chaque chunk processed file est
     * concatene dans ce fichier d'agregation apres production . En fin
     * de workflow , le fichier contient TOUS les JSON DataValue lines du
     * dataset , persistable dans {@code binaryfile.processed_data} pour
     * activer le FAST path au prochain republish .
     *
     * <p>Concatenation se fait en append-binary ({@code APPEND}) pour
     * preserver l'ordre des lignes et eviter le reparsing JSON .
     * Si la copie echoue , on log et on continue ( capture = optimisation
     * non-critique , pas de fail du workflow principal ) .
     */
    private final Path                     captureAggregateFile;

    public DataImporterTransformation(
            DataImporter             dataImporter,
            ImportProperties         importProperties,
            ImportProgressReporter   progressReporter,
            Path                     processedDir,
            String                   correlationId) {
        this(dataImporter, importProperties, progressReporter, processedDir, correlationId, null);
    }

    /**
     * Constructeur avec capture optionnelle ( Publish FAST path ) .
     *
     * @param captureAggregateFile fichier d'agregation ; chaque chunk processed
     *                             y est concatene apres son ecriture . Peut etre
     *                             {@code null} ( capture desactivee ) .
     */
    public DataImporterTransformation(
            DataImporter             dataImporter,
            ImportProperties         importProperties,
            ImportProgressReporter   progressReporter,
            Path                     processedDir,
            String                   correlationId,
            Path                     captureAggregateFile) {
        this.dataImporter         = dataImporter;
        this.importProperties     = importProperties;
        this.progressReporter     = progressReporter;
        this.processedDir         = processedDir;
        this.correlationId        = correlationId;
        this.captureAggregateFile = captureAggregateFile;
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

            // Publish FAST path : concatener ce chunk processed file dans
            // l'aggregate de capture ( si activee ) . Append binaire ,
            // preserve ordre . Echec = log + continue ( non-critique ) .
            if (captureAggregateFile != null) {
                try {
                    Files.write(captureAggregateFile,
                            Files.readAllBytes(processedPath),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                } catch (IOException ioe) {
                    org.slf4j.LoggerFactory.getLogger(DataImporterTransformation.class)
                            .warn("Publish capture failed for chunk {} : {} ( non-critical , continuing )",
                                    chunk.chunkIndex(), ioe.getMessage());
                }
            }

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