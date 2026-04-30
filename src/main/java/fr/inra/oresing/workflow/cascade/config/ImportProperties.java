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

    // ------------------------------------------------------------------ //
    //  cascade 1.7.0 strategy flags
    // ------------------------------------------------------------------ //

    /** Workflow execution mode : SYNC ( default ) or ASYNC . */
    private SinkStrategy sinkStrategy = SinkStrategy.MERGE_FILE;

    /**
     * Cascade execution mode . SYNC stays on the legacy direct-write path ;
     * ASYNC delegates to {@code workflow.executeAsync().join()} which uses
     * the parallel sink + fail-fast + sink timeout pipeline .
     */
    private fr.inrae.ore.cascade.model.workflow.ExecutionMode executionMode =
            fr.inrae.ore.cascade.model.workflow.ExecutionMode.SYNC;

    /** When true , {@code executeDirectWrite} parallelises sink writes via {@code sinkPool} + Semaphore . */
    private boolean directWriteParallel = false;

    /** Streaming strategy ( BUFFERED default ; BACKPRESSURED reserved for cascade 1.7.1 ) . */
    private fr.inrae.ore.cascade.model.workflow.StreamingMode streamingMode =
            fr.inrae.ore.cascade.model.workflow.StreamingMode.BUFFERED;

    /**
     * Staging table strategy when {@link #sinkStrategy} = DIRECT_COPY :
     * PER_CONNECTION_TEMP ( single sticky connection , atomic ) or
     * SHARED_UNLOGGED ( permanent UNLOGGED table , parallel-friendly ) .
     */
    private StagingStrategy stagingStrategy = StagingStrategy.PER_CONNECTION_TEMP;

    /** Name of the SHARED_UNLOGGED staging table ( must exist via Flyway migration ) . */
    private String stagingSharedTableName = "referencevalue_import_shared";

    /** Orphan TTL for SHARED_UNLOGGED staging table sweep ( minutes ) . */
    private int stagingSharedOrphanTtlMinutes = 60;

    /**
     * When true , {@link fr.inra.oresing.domain.data.deposit.DataImporter#prepareContextForDataTreatment}
     * skips the CSV re-encoding step and stream-copies the input file
     * after consuming the headers . Faster ( 1-3 s on 274k lines ) but
     * requires the input CSV to be free of multi-line cells / unescaped
     * quotes that would otherwise be normalised by CSVPrinter .
     */
    private boolean skipCsvReencoding = false;

    /** Strategy for the import sink path . */
    public enum SinkStrategy {
        /** Legacy : Source -&gt; Transform -&gt; MergingFileSink -&gt; merged.csv -&gt; storeAll(file) . */
        MERGE_FILE,
        /** Direct : Source -&gt; Transform -&gt; StagingPostgresSink ( COPY -&gt; staging -&gt; finalize hook -&gt; target ) . */
        DIRECT_COPY
    }

    /** Staging table strategy under SinkStrategy.DIRECT_COPY . */
    public enum StagingStrategy {
        PER_CONNECTION_TEMP,
        SHARED_UNLOGGED
    }

    public int getChunkSizeLines()        { return chunkSizeLines; }
    public int getParallelism()           { return parallelism; }
    public int getProgressBatchSize()     { return progressBatchSize; }
    public int getMaxErrorsThreshold()    { return maxErrorsThreshold; }
    public String getChunksTempDir()      { return chunksTempDir; }
    public String getProcessedTempDir()   { return processedTempDir; }
    public int getCollectorChunkSize()    { return collectorChunkSize; }
    public boolean isEnableMetrics()      { return enableMetrics; }

    public SinkStrategy getSinkStrategy() { return sinkStrategy; }
    public fr.inrae.ore.cascade.model.workflow.ExecutionMode getExecutionMode() { return executionMode; }
    public boolean isDirectWriteParallel() { return directWriteParallel; }
    public fr.inrae.ore.cascade.model.workflow.StreamingMode getStreamingMode() { return streamingMode; }
    public StagingStrategy getStagingStrategy() { return stagingStrategy; }
    public String getStagingSharedTableName() { return stagingSharedTableName; }
    public int getStagingSharedOrphanTtlMinutes() { return stagingSharedOrphanTtlMinutes; }
    public boolean isSkipCsvReencoding() { return skipCsvReencoding; }

    public void setChunkSizeLines(int v)      { this.chunkSizeLines = v; }
    public void setParallelism(int v)         { this.parallelism = v; }
    public void setProgressBatchSize(int v)   { this.progressBatchSize = v; }
    public void setMaxErrorsThreshold(int v)  { this.maxErrorsThreshold = v; }
    public void setChunksTempDir(String v)    { this.chunksTempDir = v; }
    public void setProcessedTempDir(String v) { this.processedTempDir = v; }
    public void setCollectorChunkSize(int v)  { this.collectorChunkSize = v; }
    public void setEnableMetrics(boolean v)   { this.enableMetrics = v; }

    public void setSinkStrategy(SinkStrategy v) { this.sinkStrategy = v; }
    public void setExecutionMode(fr.inrae.ore.cascade.model.workflow.ExecutionMode v) { this.executionMode = v; }
    public void setDirectWriteParallel(boolean v) { this.directWriteParallel = v; }
    public void setStreamingMode(fr.inrae.ore.cascade.model.workflow.StreamingMode v) { this.streamingMode = v; }
    public void setStagingStrategy(StagingStrategy v) { this.stagingStrategy = v; }
    public void setStagingSharedTableName(String v) { this.stagingSharedTableName = v; }
    public void setStagingSharedOrphanTtlMinutes(int v) { this.stagingSharedOrphanTtlMinutes = v; }
    public void setSkipCsvReencoding(boolean v) { this.skipCsvReencoding = v; }
}
