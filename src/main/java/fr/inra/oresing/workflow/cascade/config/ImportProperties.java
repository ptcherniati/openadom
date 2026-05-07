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

    /**
     * Parallelisme global du pipeline (nombre de workers source/transform).
     * Règle : parallelism ≤ cascade.pool.transform (défaut 4) ET ≤ hikari.maximum-pool-size - 2.
     * Ex. : pool=10 → parallelism max raisonnable = 6 (laisse 4 connexions pour le reste).
     */
    private volatile int parallelism = 4;

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
    //  Caches de pré-calcul (Axe A référentiel + Axe B Groovy)            //
    // ------------------------------------------------------------------ //

    /**
     * Plafond absolu du cache Axe A (ReferenceType.precomputedResults) par instance.
     * Au-delà, les nouvelles valeurs ne sont plus mises en cache (seenOnce continue).
     * Surcharger via CASCADE_IMPORT_REFERENCE_CACHE_MAX_ENTRIES.
     */
    private volatile int referenceCacheMaxEntries = 5000;

    /**
     * Plafond du cache Axe B (Groovy expression results) par instance.
     * Surcharger via CASCADE_IMPORT_GROOVY_CACHE_MAX_ENTRIES.
     */
    private volatile int groovyCacheMaxEntries = 1000;

    /**
     * Active le mode « récursion ordonnée » globalement : les parents sont garantis
     * d'apparaître <em>avant</em> leurs enfants dans le CSV récursif.
     * En mode ordonné, un parent introuvable génère une erreur immédiate au lieu
     * d'être différé dans missingParentLine.
     * Par tag YAML : {@code __ORDER_STRICT__} (activation par datatype).
     * Ce flag est le fallback global pour tous les datatypes.
     */
    private volatile boolean orderedRecursionMode = false;

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
     * Capacite de la {@link java.util.concurrent.ArrayBlockingQueue}
     * inter-stage transform / sink utilisee uniquement quand
     * {@link #pipelineMode} = PIPELINED ( cf cascade 2.1.0 ) . Trop bas
     * ( ex. 2 ) provoque du backpressure inutile : le sink ne consomme
     * pas assez vite pour vider la queue , les workers transform
     * bloquent en attendant un slot . Trop haut consomme inutilement de
     * la heap ( N chunks bufferises ) . Defaut 50 : compromis transform
     * 6-12 min vs sink secondes/minutes ( cascade perf benchmarks ) .
     *
     * <p>Surcharge via env var {@code CASCADE_IMPORT_PIPELINE_QUEUE_CAPACITY}
     * ou property {@code cascade.import.pipeline-queue-capacity} . Mutable
     * via ConfigEditPanel admin .
     *
     * @since AUDIT 06-05-26 #6
     */
    private volatile int pipelineQueueCapacity = 50;

    /**
     * Staging table strategy when {@link #sinkStrategy} = DIRECT_COPY .
     *
     * <p><b>Defaut prod : SHARED_UNLOGGED</b> ( table UNLOGGED partagee
     * tag correlation_id ) car elle survit au crash JVM ( docker kill ,
     * OOM ) et permet la recuperation des rows via IntegrityService +
     * sweeper orphan . PER_CONNECTION_TEMP ( ancien defaut ) est
     * irrecuperable : la TEMP TABLE meurt avec la connexion , aucune
     * trace en base . PER_CONNECTION_TEMP est conserve pour les tests
     * d'integration ( atomicite simple a raisonner ) .
     *
     * <p>Surcharge possible via env var {@code CASCADE_IMPORT_STAGING_STRATEGY}
     * ou property {@code cascade.import.staging-strategy} .
     */
    private volatile StagingStrategy stagingStrategy = StagingStrategy.SHARED_UNLOGGED;

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

    /**
     * Postgres {@code SET LOCAL statement_timeout} applique au debut de
     * {@link fr.inra.oresing.workflow.cascade.StagingFinalizeSql#runFinalize}
     * ( en minutes , 0 = pas de timeout ) . Garde-fou contre les UPSERTs
     * bloques infiniment ( deadlock pur , lock advisory non release ) que
     * le heartbeat ne detecte pas ( cf Point 4 javadoc ) . Doit etre
     * superieur au temps legitime maximal d'un finalize ( gros UPSERT
     * 1M+ rows ) . Defaut : 180 min ( 3h ) .
     *
     * <p>Surcharge via env var {@code CASCADE_IMPORT_FINALIZE_STATEMENT_TIMEOUT_MINUTES}
     * ou property {@code cascade.import.finalize-statement-timeout-minutes} .
     */
    private volatile int finalizeStatementTimeoutMinutes = 180;

    /** Strategy for the import sink path . */
    public enum SinkStrategy {
        /** Legacy : Source -&gt; Transform -&gt; MergingFileSink -&gt; merged.csv -&gt; storeAll(file) . */
        MERGE_FILE,
        /** Direct : Source -&gt; Transform -&gt; StagingPostgresSink ( COPY -&gt; staging -&gt; finalize hook -&gt; target ) . */
        DIRECT_COPY
    }

    /**
     * Staging table strategy . Pertinent uniquement avec
     * {@link SinkStrategy#DIRECT_COPY} : decrit ou cascade depose les chunks
     * pendant le COPY parallele , avant que le finalize hook UPSERT vers la
     * table finale .
     *
     * <p>Avec {@link SinkStrategy#MERGE_FILE} le champ est ignore : MERGE_FILE
     * concatene les chunks sur disque local puis fait 1 COPY direct vers la
     * table finale ( pas de staging cascade ) . Le couplage UI/validation
     * empeche desormais cette combinaison ambigue ( cf
     * {@code MergeFileWithStagingRule} ) .
     */
    public enum StagingStrategy {
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
    public int getParallelism()            { return parallelism; }
    public int getProgressBatchSize()     { return progressBatchSize; }    public int getMaxErrorsThreshold()    { return maxErrorsThreshold; }
    public String getChunksTempDir()      { return chunksTempDir; }
    public String getProcessedTempDir()   { return processedTempDir; }
    public int getCollectorChunkSize()    { return collectorChunkSize; }
    public boolean isEnableMetrics()      { return enableMetrics; }

    public SinkStrategy getSinkStrategy() { return sinkStrategy; }
    public fr.inrae.ore.cascade.model.workflow.PipelineMode getPipelineMode() { return pipelineMode; }
    public int getPipelineQueueCapacity() { return pipelineQueueCapacity; }
    public StagingStrategy getStagingStrategy() { return stagingStrategy; }
    public String getStagingSharedTableName() { return stagingSharedTableName; }
    public int getStagingSharedOrphanTtlMinutes() { return stagingSharedOrphanTtlMinutes; }
    public boolean isSkipCsvReencoding() { return skipCsvReencoding; }
    public int getFinalizeStatementTimeoutMinutes() { return finalizeStatementTimeoutMinutes; }
    public int getReferenceCacheMaxEntries()  { return referenceCacheMaxEntries; }
    public int getGroovyCacheMaxEntries()     { return groovyCacheMaxEntries; }
    public boolean isOrderedRecursionMode()   { return orderedRecursionMode; }

    public void setChunkSizeLines(int v)      { this.chunkSizeLines = v; }
    public void setParallelism(int v)          { this.parallelism = v; }
    public void setProgressBatchSize(int v)   { this.progressBatchSize = v; }
    public void setMaxErrorsThreshold(int v)  { this.maxErrorsThreshold = v; }
    public void setChunksTempDir(String v)    { this.chunksTempDir = v; }
    public void setProcessedTempDir(String v) { this.processedTempDir = v; }
    public void setCollectorChunkSize(int v)  { this.collectorChunkSize = v; }
    public void setEnableMetrics(boolean v)   { this.enableMetrics = v; }

    public void setSinkStrategy(SinkStrategy v) { this.sinkStrategy = v; }
    public void setPipelineMode(fr.inrae.ore.cascade.model.workflow.PipelineMode v) { this.pipelineMode = v; }
    public void setPipelineQueueCapacity(int v) { this.pipelineQueueCapacity = v; }
    public void setStagingStrategy(StagingStrategy v) { this.stagingStrategy = v; }
    public void setStagingSharedTableName(String v) { this.stagingSharedTableName = v; }
    public void setStagingSharedOrphanTtlMinutes(int v) { this.stagingSharedOrphanTtlMinutes = v; }
    public void setSkipCsvReencoding(boolean v) { this.skipCsvReencoding = v; }
    public void setFinalizeStatementTimeoutMinutes(int v) { this.finalizeStatementTimeoutMinutes = v; }
    public void setReferenceCacheMaxEntries(int v) { this.referenceCacheMaxEntries = v; }
    public void setGroovyCacheMaxEntries(int v)  { this.groovyCacheMaxEntries = v; }
    public void setOrderedRecursionMode(boolean v) { this.orderedRecursionMode = v; }
}