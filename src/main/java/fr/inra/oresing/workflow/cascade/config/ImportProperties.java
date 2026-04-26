package fr.inra.oresing.workflow.cascade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration du pipeline d'import CSV via cascade.
 *
 * <p>Prefix Spring : {@code cascade.import}. Toutes les valeurs ont des
 * defauts raisonnables ; surchargeables via env vars
 * ( {@code CASCADE_IMPORT_CHUNK_SIZE_LINES} , etc. ).
 */
@Configuration
@ConfigurationProperties(prefix = "cascade.import")
public class ImportProperties {

    /** Nombre de lignes CSV par chunk. */
    private int chunkSizeLines = 1000;

    /** Niveau de parallélisme appliqué aux Transformations cascade ( workers ). */
    private int parallelism = 2;

    /** Granularité des notifications de progression ( en lignes ). */
    private int progressBatchSize = 100;

    /** Seuil d'erreurs au-delà duquel le workflow est avorté. */
    private int maxErrorsThreshold = 100;

    /** Répertoire racine pour les chunks bruts découpés du fichier source. */
    private String chunksTempDir = "/tmp/openadom-import/chunks";

    /** Répertoire racine pour les chunks transformés ( sortie DataImporter ). */
    private String processedTempDir = "/tmp/openadom-import/processed";

    /**
     * Taille de consolidation côté Collector cascade ( 0 = pas de
     * consolidation , -1 = merge tout , &gt;0 = consolide à cette taille ).
     */
    private int collectorChunkSize = 0;

    /**
     * Active les interceptors {@code MetricsChunkInterceptor} +
     * {@code MetricsWorkflowInterceptor} ( + JVM stats start/end ).
     * False par défaut pour minimiser le coût CPU/RAM des imports
     * ( la collection a un coût non nul sur les très gros volumes ).
     */
    private boolean enableMetrics = false;

    public int getChunkSizeLines()        { return chunkSizeLines; }
    public int getParallelism()           { return parallelism; }
    public int getProgressBatchSize()     { return progressBatchSize; }
    public int getMaxErrorsThreshold()    { return maxErrorsThreshold; }
    public String getChunksTempDir()      { return chunksTempDir; }
    public String getProcessedTempDir()   { return processedTempDir; }
    public int getCollectorChunkSize()    { return collectorChunkSize; }
    public boolean isEnableMetrics()      { return enableMetrics; }

    public void setChunkSizeLines(int v)      { this.chunkSizeLines = v; }
    public void setParallelism(int v)         { this.parallelism = v; }
    public void setProgressBatchSize(int v)   { this.progressBatchSize = v; }
    public void setMaxErrorsThreshold(int v)  { this.maxErrorsThreshold = v; }
    public void setChunksTempDir(String v)    { this.chunksTempDir = v; }
    public void setProcessedTempDir(String v) { this.processedTempDir = v; }
    public void setCollectorChunkSize(int v)  { this.collectorChunkSize = v; }
    public void setEnableMetrics(boolean v)   { this.enableMetrics = v; }
}
