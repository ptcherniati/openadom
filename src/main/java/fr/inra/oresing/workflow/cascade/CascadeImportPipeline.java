package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.progress.ImportProgressReporter;
import fr.inrae.ore.cascade.api.workflow.builder.WorkflowBuilder;
import fr.inrae.ore.cascade.model.workflow.ProcessingStatus;
import fr.inrae.ore.cascade.model.workflow.Workflow;
import fr.inrae.ore.cascade.model.workflow.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Orchestration de l'import CSV → PostgreSQL via la bibliothèque cascade.
 *
 * <p>Phase 1e (#62) : aucune dependance file-processor. Le pipeline est
 * compose de trois briques cascade :
 * <ul>
 *   <li>{@link FileChunkSource} - decoupe le fichier en chunks CSV sur disque</li>
 *   <li>{@link DataImporterTransformation} - delegue le traitement metier
 *       au {@link DataImporter} existant</li>
 *   <li>{@link MergingFileSink} - concatene les chunks traites en un seul CSV</li>
 * </ul>
 *
 * <p>Le chargement final en base reste assure par
 * {@link DataRepository#storeAll}. La progression est tracee via
 * {@link ImportProgressReporter}, le cleanup via {@link WorkflowTempCleanup}.
 * Le rate-limit n'est plus actif sur cet endpoint en phase 1e ; il sera
 * reintroduit via cascade {@code UserRateLimiter} si necessaire.
 */
@Slf4j
@Service
public class CascadeImportPipeline {

    private final ImportProperties        importProperties;
    private final ImportProgressReporter  progressReporter;
    private final WorkflowTempCleanup     tempCleanup;

    public CascadeImportPipeline(
            ImportProperties       importProperties,
            ImportProgressReporter progressReporter,
            WorkflowTempCleanup    tempCleanup) {
        this.importProperties  = importProperties;
        this.progressReporter  = progressReporter;
        this.tempCleanup       = tempCleanup;
    }

    /**
     * Lance un import pour un fichier CSV sans en-tete deja prepare par
     * {@link DataImporter#prepareContextForDataTreatment}.
     */
    public void execute(
            DataImporter   dataImporter,
            DataRepository referenceValueRepository,
            Path           headerlessCsv,
            String         userId) {

        if (!Files.exists(headerlessCsv)) {
            throw new IllegalArgumentException("Input file does not exist: " + headerlessCsv);
        }

        final String correlationId = UUID.randomUUID().toString();

        final Path uploadedPath;
        try {
            uploadedPath = uploadFile(headerlessCsv, userId, correlationId);
        } catch (IOException e) {
            log.error("Erreur lors de la preparation de l'upload pour {} : {}", userId, e.getMessage(), e);
            throw new UnsupportedOperationException("Failed to prepare workflow", e);
        }

        final int chunkSizeLines = importProperties.getChunkSizeLines();
        final int parallelism    = importProperties.getParallelism();
        final int maxErrors      = importProperties.getMaxErrorsThreshold();

        final Path chunksDir    = Paths.get(importProperties.getChunksTempDir(), userId, correlationId);
        final Path processedDir = Paths.get(importProperties.getProcessedTempDir(), correlationId);
        final Path mergedPath   = Paths.get(System.getProperty("java.io.tmpdir"),
                                            "openadom-import-" + correlationId + ".csv");

        FileChunkSource source = new FileChunkSource(uploadedPath, chunkSizeLines, chunksDir);

        DataImporterTransformation transformation = new DataImporterTransformation(
                dataImporter,
                importProperties,
                progressReporter,
                processedDir,
                correlationId);

        MergingFileSink sink = new MergingFileSink(mergedPath);

        log.info("[{}] Demarrage import : user={}, file={}, chunkSize={}, parallelism={}, maxErrors={}",
                correlationId, userId, uploadedPath.getFileName(), chunkSizeLines, parallelism, maxErrors);

        Workflow workflow = WorkflowBuilder.create()
                .forUser(userId)
                .from(source)
                .transform(transformation)
                .to(sink)
                .withCorrelationId(correlationId)
                .withParallelism(parallelism)
                .withMaxErrors(maxErrors)
                .build();

        try {
            WorkflowResult result = workflow.execute();
            if (result.status() == ProcessingStatus.FAILED) {
                String firstError = result.errors().isEmpty()
                        ? result.fatalError().map(Throwable::getMessage).orElse("unknown error")
                        : result.errors().get(0);
                log.error("[{}] Workflow cascade en echec : {}", correlationId, firstError);
                tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);
                throw new UnsupportedOperationException("Import workflow failed: " + firstError);
            }

            log.info("[{}] Workflow cascade termine : processed={}, chunks={}, duration={}",
                    correlationId, result.recordsProcessed(), result.chunksProcessed(), result.duration());

            dataImporter.treatErrors();
            referenceValueRepository.storeAll(sink.getMergedPath());

            tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);

        } catch (RuntimeException e) {
            tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);
            throw e;
        }
    }

    /**
     * Deplace le fichier source dans un repertoire dedie au user et au
     * correlationId, puis renvoie le chemin final.
     */
    private Path uploadFile(Path source, String userId, String correlationId) throws IOException {
        Path uploadDir = Paths.get(importProperties.getChunksTempDir(), "uploads", userId);
        Files.createDirectories(uploadDir);

        String fileName = source.getFileName().toString();
        Path target = uploadDir.resolve(correlationId + "_" + fileName);
        Files.move(source, target, REPLACE_EXISTING);

        log.debug("[{}] Fichier deplace : {} -> {}", correlationId, source, target);
        return target;
    }
}
