package fr.inra.oresing.workflow.cascade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration de l'import CSV via cascade. Remplace l'ancienne
 * {@code WorkflowProperties} du JAR file-processor.
 *
 * <p>Prefix Spring : {@code app.import}. Toutes les valeurs ont des
 * defauts raisonnables ; surchargeables via env vars
 * ({@code APP_IMPORT_CHUNK_SIZE_LINES}, etc.).
 */
@Configuration
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    /** Nombre de lignes CSV par chunk. */
    private int chunkSizeLines = 1000;

    /** Niveau de parallelisme appliqué aux Transformations cascade (workers). */
    private int parallelism = 2;

    /** Granularite des notifications de progression (en lignes). */
    private int progressBatchSize = 100;

    /** Seuil d'erreurs au-dela duquel le workflow est avorté. */
    private int maxErrorsThreshold = 100;

    /** Repertoire racine pour les chunks bruts decoupes du fichier source. */
    private String chunksTempDir = "/tmp/openadom-import/chunks";

    /** Repertoire racine pour les chunks transformés (sortie DataImporter). */
    private String processedTempDir = "/tmp/openadom-import/processed";

    public int getChunkSizeLines()        { return chunkSizeLines; }
    public int getParallelism()           { return parallelism; }
    public int getProgressBatchSize()     { return progressBatchSize; }
    public int getMaxErrorsThreshold()    { return maxErrorsThreshold; }
    public String getChunksTempDir()      { return chunksTempDir; }
    public String getProcessedTempDir()   { return processedTempDir; }

    public void setChunkSizeLines(int v)      { this.chunkSizeLines = v; }
    public void setParallelism(int v)         { this.parallelism = v; }
    public void setProgressBatchSize(int v)   { this.progressBatchSize = v; }
    public void setMaxErrorsThreshold(int v)  { this.maxErrorsThreshold = v; }
    public void setChunksTempDir(String v)    { this.chunksTempDir = v; }
    public void setProcessedTempDir(String v) { this.processedTempDir = v; }
}
