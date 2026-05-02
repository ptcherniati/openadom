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

    // Tous les fields sont {@code volatile} pour autoriser la mutation
    // a chaud par {@code ConfigEditService} ( endpoint admin oa-live ) .
    // Les readers ( {@code CascadeImportPipeline.execute} ) lisent chaque
    // valeur une seule fois au build du Workflow , donc une mutation
    // pendant un import en cours ne casse pas l'import courant : seule
    // la prochaine soumission verra la nouvelle valeur .

    /** Nombre de lignes CSV par chunk. */
    private volatile int chunkSizeLines = 1000;

    /** Granularité des notifications de progression ( en lignes ). */
    private volatile int progressBatchSize = 100;

    /** Seuil d'erreurs au-delà duquel le workflow est avorté. */
    private volatile int maxErrorsThreshold = 100;

    /** Répertoire racine pour les chunks bruts découpés du fichier source. */
    private volatile String chunksTempDir = "/tmp/openadom-import/chunks";

    /** Répertoire racine pour les chunks transformés ( sortie DataImporter ). */
    private volatile String processedTempDir = "/tmp/openadom-import/processed";

    /**
     * Taille de consolidation côté Collector cascade ( 0 = pas de
     * consolidation , -1 = merge tout , &gt;0 = consolide à cette taille ).
     */
    private volatile int collectorChunkSize = 0;

    /**
     * Active les interceptors {@code MetricsChunkInterceptor} +
     * {@code MetricsWorkflowInterceptor} ( + JVM stats start/end ).
     * False par défaut pour minimiser le coût CPU/RAM des imports
     * ( la collection a un coût non nul sur les très gros volumes ).
     */
    private volatile boolean enableMetrics = false;

    // ------------------------------------------------------------------ //
    //  cascade 1.7.0 strategy flags
    // ------------------------------------------------------------------ //

    /** Sink strategy ( MERGE_FILE legacy , DIRECT_COPY production ) . */
    private volatile SinkStrategy sinkStrategy = SinkStrategy.MERGE_FILE;

    /**
     * Cascade pipeline mode ( cascade 2.1.0 ) :
     * {@link fr.inrae.ore.cascade.model.workflow.PipelineMode#STAGED STAGED}
     * waits for every chunk's transform to complete before sink begins ;
     * {@link fr.inrae.ore.cascade.model.workflow.PipelineMode#PIPELINED PIPELINED}
     * pipes chunks through a bounded queue for transform / sink overlap .
     */
    private volatile fr.inrae.ore.cascade.model.workflow.PipelineMode pipelineMode =
            fr.inrae.ore.cascade.model.workflow.PipelineMode.STAGED;

    /**
     * Staging table strategy when {@link #sinkStrategy} = DIRECT_COPY :
     * PER_CONNECTION_TEMP ( single sticky connection , atomic ) or
     * SHARED_UNLOGGED ( permanent UNLOGGED table , parallel-friendly ) .
     */
    private volatile StagingStrategy stagingStrategy = StagingStrategy.PER_CONNECTION_TEMP;

    /** Name of the SHARED_UNLOGGED staging table ( must exist via Flyway migration ) . */
    private volatile String stagingSharedTableName = "oa_staging.referencevalue_import_shared";

    /** Orphan TTL for SHARED_UNLOGGED staging table sweep ( minutes ) . */
    private volatile int stagingSharedOrphanTtlMinutes = 60;

    /**
     * When true , {@link fr.inra.oresing.domain.data.deposit.DataImporter#prepareContextForDataTreatment}
     * skips the CSV re-encoding step and stream-copies the input file
     * after consuming the headers . Faster ( 1-3 s on 274k lines ) but
     * requires the input CSV to be free of multi-line cells / unescaped
     * quotes that would otherwise be normalised by CSVPrinter .
     */
    private volatile boolean skipCsvReencoding = false;

    /** Strategy for the import sink path . */
    public enum SinkStrategy {
        /** Legacy : Source -&gt; Transform -&gt; MergingFileSink -&gt; merged.csv -&gt; storeAll(file) . */
        MERGE_FILE,
        /** Direct : Source -&gt; Transform -&gt; StagingPostgresSink ( COPY -&gt; staging -&gt; finalize hook -&gt; target ) . */
        DIRECT_COPY
    }

    /** Staging table strategy . */
    public enum StagingStrategy {
        /**
         * Bypass complet de la staging table . Avec {@link SinkStrategy#MERGE_FILE}
         * : c'est le comportement par defaut ( chunks merges sur disque puis 1
         * COPY massif direct vers la table finale ) . Avec
         * {@link SinkStrategy#DIRECT_COPY} : non implemente ( bloque par la
         * rule {@code DirectCopyNoStagingNotSupported} ) ; reserve pour un
         * futur sink direct-to-final sans table intermediaire .
         */
        NO_STAGING,
        /** TEMP table per-conn ( sticky , 1 sink serie , auto-cleanup ) . */
        PER_CONNECTION_TEMP,
        /** Table UNLOGGED partagee + tag correlation_id ( workers paralleles ) . */
        SHARED_UNLOGGED,
        /**
         * 1 table UNLOGGED dediee creee/droppee par workflow
         * ( oa_staging.referencevalue_import_&lt;correlationId&gt; ) . Workers
         * paralleles , isolation native , DROP TABLE atomique , sweeper
         * orphan scanne pg_class apres TTL .
         */
        PER_WORKFLOW_TABLE
    }

    public int getChunkSizeLines()        { return chunkSizeLines; }
    public int getProgressBatchSize()     { return progressBatchSize; }
    public int getMaxErrorsThreshold()    { return maxErrorsThreshold; }
    public String getChunksTempDir()      { return chunksTempDir; }
    public String getProcessedTempDir()   { return processedTempDir; }
    public int getCollectorChunkSize()    { return collectorChunkSize; }
    public boolean isEnableMetrics()      { return enableMetrics; }

    public SinkStrategy getSinkStrategy() { return sinkStrategy; }
    public fr.inrae.ore.cascade.model.workflow.PipelineMode getPipelineMode() { return pipelineMode; }
    public StagingStrategy getStagingStrategy() { return stagingStrategy; }
    public String getStagingSharedTableName() { return stagingSharedTableName; }
    public int getStagingSharedOrphanTtlMinutes() { return stagingSharedOrphanTtlMinutes; }
    public boolean isSkipCsvReencoding() { return skipCsvReencoding; }

    public void setChunkSizeLines(int v)      { this.chunkSizeLines = v; }
    public void setProgressBatchSize(int v)   { this.progressBatchSize = v; }
    public void setMaxErrorsThreshold(int v)  { this.maxErrorsThreshold = v; }
    public void setChunksTempDir(String v)    { this.chunksTempDir = v; }
    public void setProcessedTempDir(String v) { this.processedTempDir = v; }
    public void setCollectorChunkSize(int v)  { this.collectorChunkSize = v; }
    public void setEnableMetrics(boolean v)   { this.enableMetrics = v; }

    public void setSinkStrategy(SinkStrategy v) { this.sinkStrategy = v; }
    public void setPipelineMode(fr.inrae.ore.cascade.model.workflow.PipelineMode v) { this.pipelineMode = v; }
    public void setStagingStrategy(StagingStrategy v) { this.stagingStrategy = v; }
    public void setStagingSharedTableName(String v) { this.stagingSharedTableName = v; }
    public void setStagingSharedOrphanTtlMinutes(int v) { this.stagingSharedOrphanTtlMinutes = v; }
    public void setSkipCsvReencoding(boolean v) { this.skipCsvReencoding = v; }
}
