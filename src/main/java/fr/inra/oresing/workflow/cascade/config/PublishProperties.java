package fr.inra.oresing.workflow.cascade.config;

import fr.inrae.ore.cascade.model.workflow.PipelineMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration des flux de republication ( PublishLifecycleService :
 * toggle publish / unpublish / delete file ) .
 *
 * <p>Prefix Spring : {@code openadom.publish} . Surchargeables via env
 * vars ( {@code OPENADOM_PUBLISH_CHUNK_SIZE_LINES} , etc . ) .
 *
 * <p><b>Pourquoi un bean distinct de {@link ImportProperties} ?</b>
 * Les flux republication ont des contraintes differentes du flux upload
 * initial :
 *
 * <ul>
 *   <li>les donnees sont <b>deja validees</b> ( le 1er upload a passe
 *       parse + checks + cascade pipeline complet ) ; le republish
 *       peut accepter des commits partiels ( rollback non strict ) ;</li>
 *   <li>la republication est <b>concurrente</b> avec d'autres workflows
 *       en cours ( upload , extractions , polling oa-live ) ; on veut
 *       minimiser la pression memoire pour ne pas degrader le reste ;</li>
 *   <li>le user a deja le feedback flag inverse ( pastille pub/depub
 *       instantanee post phase 1 ) ; on peut accepter une phase 2
 *       moins rapide sans degrader UX .</li>
 * </ul>
 *
 * <p><b>Defauts memoire-friendly :</b> STREAMING + chunk 200 +
 * parallelism 2 . Surchargeables par environnement .
 *
 * <p>Mutation a chaud : tous les fields sont {@code volatile} , editables
 * via l'endpoint admin {@code ConfigEditController} + UI oa-live , au
 * meme titre que {@link ImportProperties} .
 *
 * @author R.YAHIAOUI
 */
@Configuration
@ConfigurationProperties(prefix = "openadom.publish")
public class PublishProperties {

    /**
     * Pipeline mode . PIPELINED = chunks pipent transform / sink en
     * parallele via une queue bornee ( memoire stable bornee a la
     * queue capacity ) . STAGED = transform de tous les chunks puis
     * sink ( pic memoire eleve = N chunks bufferises ) . Default
     * PIPELINED car republish privilegie memoire basse .
     */
    private volatile PipelineMode pipelineMode = PipelineMode.PIPELINED;

    /**
     * Sink strategy . MERGE_FILE = temp files locaux + 1 COPY final ;
     * DIRECT_COPY = N workers COPY parallel direct vers staging .
     *
     * <p><b>Default DIRECT_COPY</b> ( !=ImportProperties default ) :
     * MERGE_FILE force {@code pipelineMode = STAGED} en backend ( cascade
     * PIPELINED ne supporte pas les Collectors ) , annulant le gain
     * memoire vise par PublishProperties . DIRECT_COPY permet effectivement
     * PIPELINED -&gt; chunks pipent transform / sink en parallel via bounded
     * queue , memoire stable et basse .
     */
    private volatile ImportProperties.SinkStrategy sinkStrategy =
            ImportProperties.SinkStrategy.DIRECT_COPY;

    /**
     * Staging strategy . Pertinent uniquement si {@link #sinkStrategy} =
     * DIRECT_COPY . SHARED_UNLOGGED partage la meme table staging entre
     * tous les workflows ( tag correlation_id ) ; PER_WORKFLOW_TABLE
     * cree/drop une table par workflow .
     */
    private volatile ImportProperties.StagingStrategy stagingStrategy =
            ImportProperties.StagingStrategy.SHARED_UNLOGGED;

    /**
     * Parallelisme du pipeline cascade ( source / transform workers ) .
     * Default 2 ( vs 4 pour upload ) : minimise concurrence avec autres
     * workflows .
     */
    private volatile int parallelism = 2;

    /**
     * Taille des chunks CSV en lignes . Default 1000 ( aligne sur upload )
     * - bon trade-off throughput / memoire pour la republication moderne
     * ( lite mode + FAST path = pas de bottleneck heap a 1000 lignes ) .
     */
    private volatile int chunkSizeLines = 1000;

    /**
     * Seuil d'erreurs avant abort . Default 100 ( identique upload :
     * la data est deja validee donc 0 erreur attendue ; le seuil est
     * un garde-fou ) .
     */
    private volatile int maxErrorsThreshold = 100;

    // ========================================================================
    // Mode pipeline publication ( cf PUBLISH_PIPELINE.md )
    // ========================================================================

    /**
     * Strategie globale du pipeline publish/unpublish/republish .
     *
     * <ul>
     *   <li>{@link PublishMode#CASCADE_ALWAYS} : legacy . Chaque republish
     *       re-execute cascade ( LITE ou FULL selon configHash ) depuis
     *       {@code binaryfile.filedata} . Si {@link #captureProcessedEnabled}
     *       , capture pendant cascade pour armer FAST path subsequent .
     *       Cache {@code processed_data} cree au 1er republish et garde a vie .</li>
     *
     *   <li>{@link PublishMode#CACHED_ROTATION} : rotation cache lifecycle .
     *       <ol>
     *         <li>Unpublish : SQL snapshot {@code referencevalue} -&gt;
     *             {@code binaryfile.processed_data} AVANT delete rows .</li>
     *         <li>Republish : COPY {@code processed_data} -&gt;
     *             {@code referencevalue} ( via staging ou direct ) APUIS
     *             clear {@code processed_data} = NULL .</li>
     *       </ol>
     *       Cache existe UNIQUEMENT pendant fenetre unpublished
     *       ( pas de duplication storage en steady state publie ) .</li>
     * </ul>
     */
    private volatile PublishMode publishMode = PublishMode.CACHED_ROTATION;

    // ========================================================================
    // FAST path / cache processed_data ( legacy CASCADE_ALWAYS mode flags )
    // ========================================================================

    /**
     * Active le FAST path republish : si {@code binaryfile.processed_data}
     * cache est disponible + {@code configHash} matche -> {@code COPY
     * processed_data -> staging -> referencevalue} directement , bypass
     * complet {@code DataImporter} . ~5s pour 875K rows , heap ~10 MB .
     *
     * <p>Pertinent pour {@link PublishMode#CASCADE_ALWAYS} . En mode
     * {@link PublishMode#CACHED_ROTATION} , le FAST path est toujours
     * tente apres un unpublish ( le snapshot etant garanti ) .
     */
    private volatile boolean fastPathEnabled = true;

    /**
     * Pour {@link PublishMode#CASCADE_ALWAYS} : active la capture du JSON
     * processed pendant cascade ( DataImporter ecrit chaque ligne dans
     * un temp file persiste apres en {@code processed_data} ) .
     * Permet d'armer FAST path subsequent .
     *
     * <p>Ignore en {@link PublishMode#CACHED_ROTATION} ( cache est alimente
     * via snapshot SQL au unpublish , pas via capture cascade ) .
     */
    private volatile boolean captureProcessedEnabled = true;

    /**
     * Met a jour le {@code configHash} stocke dans
     * {@code binaryfile.params.configHash} apres un republish reussi en
     * mode FULL ( hash mismatch detecte ) . Permet au prochain republish
     * de basculer en LITE ou FAST si la config reste stable .
     */
    private volatile boolean refreshHashOnFullPath = true;

    /**
     * Timeout d'acquisition du synthesisLock par (app, datatype) en Phase 2 .
     * Default 5 min . Volatile - editable a chaud via oa-live AdminSystem ;
     * sous charge l'admin peut le bumper sans redemarrer le backend pour
     * eviter les FAIL spurious quand un workflow long tient le lock . Fix P1-BACK-4 .
     */
    private volatile int synthesisLockTimeoutMinutes = 5;

    /**
     * Strategie de pipeline publication . Cf {@link #publishMode} doc .
     */
    public enum PublishMode {
        CASCADE_ALWAYS,
        CACHED_ROTATION
    }

    public PipelineMode getPipelineMode()                          { return pipelineMode; }
    public ImportProperties.SinkStrategy getSinkStrategy()         { return sinkStrategy; }
    public ImportProperties.StagingStrategy getStagingStrategy()   { return stagingStrategy; }
    public int getParallelism()                                    { return parallelism; }
    public int getChunkSizeLines()                                 { return chunkSizeLines; }
    public int getMaxErrorsThreshold()                             { return maxErrorsThreshold; }
    public boolean isFastPathEnabled()                             { return fastPathEnabled; }
    public boolean isCaptureProcessedEnabled()                     { return captureProcessedEnabled; }
    public boolean isRefreshHashOnFullPath()                       { return refreshHashOnFullPath; }
    public PublishMode getPublishMode()                            { return publishMode; }
    public int getSynthesisLockTimeoutMinutes()                    { return synthesisLockTimeoutMinutes; }
    public void setSynthesisLockTimeoutMinutes(int v)              { this.synthesisLockTimeoutMinutes = v; }

    public void setPipelineMode(PipelineMode v)                          { this.pipelineMode = v; }
    public void setSinkStrategy(ImportProperties.SinkStrategy v)         { this.sinkStrategy = v; }
    public void setStagingStrategy(ImportProperties.StagingStrategy v)   { this.stagingStrategy = v; }
    public void setParallelism(int v)                                    { this.parallelism = v; }
    public void setChunkSizeLines(int v)                                 { this.chunkSizeLines = v; }
    public void setMaxErrorsThreshold(int v)                             { this.maxErrorsThreshold = v; }
    public void setFastPathEnabled(boolean v)                            { this.fastPathEnabled = v; }
    public void setCaptureProcessedEnabled(boolean v)                    { this.captureProcessedEnabled = v; }
    public void setRefreshHashOnFullPath(boolean v)                      { this.refreshHashOnFullPath = v; }
    public void setPublishMode(PublishMode v)                            { this.publishMode = v; }

    /** Construit un {@link CascadeRuntimeOverride} reflet de cette config . */
    public CascadeRuntimeOverride toRuntimeOverride() {
        return new CascadeRuntimeOverride(
                pipelineMode, sinkStrategy, stagingStrategy,
                parallelism, chunkSizeLines, maxErrorsThreshold);
    }
}
