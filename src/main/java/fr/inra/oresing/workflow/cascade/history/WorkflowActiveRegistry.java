package fr.inra.oresing.workflow.cascade.history;

import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import fr.inrae.ore.cascade.model.listener.WorkflowEvents;
import fr.inrae.ore.cascade.model.listener.WorkflowListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory registry of workflows currently in progress.
 *
 * <p>The oa_audit.workflow_log table only receives rows when a workflow
 * finishes ( COMPLETED / FAILED / CANCELLED / RATE_LIMITED ). For the
 * live dashboard oa-live we need a snapshot of running workflows with their
 * latest progress values ; that snapshot lives in this thread-safe registry.
 *
 * <p>Entries are added on {@link #start(WorkflowSnapshot)} by the
 * orchestrators , updated on every progress event via
 * {@link #update(UUID, long, long, int, Double, long)} , and removed by
 * {@link #finish(UUID)} once the workflow completes.
 *
 * <p>Per-chunk drill-down ( plan E ) : the registry subscribes itself as
 * a cascade {@link WorkflowListener} on
 * {@link WorkflowEventBus#getInstance()} ; the
 * {@code onChunkStart / onChunkProgress / onChunkEnd} hooks update a
 * separate per-workflow {@link ChunkSnapshot} map merged at read time.
 *
 * <p>Phase 3 dashboard ( issue #62 ).
 */
@Service
@Slf4j
public class WorkflowActiveRegistry implements WorkflowListener {

    /**
     * Lecteur live des tailles de pools cascade . Sert a remplacer , au
     * moment du listing , la valeur {@link ParallelismSnapshot} figee au
     * demarrage du workflow par la taille courante du pool ( permet a
     * l'admin de voir les changements de pool size en temps reel dans le
     * header oa-live ) . Optionnel : si null , la valeur figee est
     * conservee ( comportement legacy ) .
     */
    private final fr.inra.oresing.workflow.cascade.config.PoolReloader poolReloader;

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowActiveRegistry(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            fr.inra.oresing.workflow.cascade.config.PoolReloader poolReloader) {
        this.poolReloader = poolReloader;
    }

    /** Constructeur de test ( sans live pool reloader ) . */
    public WorkflowActiveRegistry() {
        this.poolReloader = null;
    }

    private final ConcurrentMap<UUID, WorkflowSnapshot> byCorrelationId = new ConcurrentHashMap<>();

    /** Per-workflow chunk state ( ConcurrentHashMap of ConcurrentHashMap ). */
    private final ConcurrentMap<UUID, ConcurrentMap<Integer, ChunkSnapshot>> chunksByCorrelationId =
            new ConcurrentHashMap<>();

    /**
     * Per-workflow per-worker SOURCE / SINK stats . Cascade 1.9.0+ emits
     * chunk events ONLY for the transform stage ; source / sink stages
     * publish dedicated lifecycle events
     * ( {@code SourceFetchStart} / {@code SourceChunkEmitted} ,
     *   {@code SinkChunkAccepted} / {@code SinkChunkWritten} ) . We
     * accumulate them into lightweight {@link StageWorkerStat} entries so
     * the {@link WorkerSnapshot} aggregation can emit one row per stage
     * worker - and the oa-live Workers view shows the full pipeline ,
     * not only transform .
     */
    private final ConcurrentMap<UUID, ConcurrentMap<String, StageWorkerStat>> sourceWorkersByCid =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, StageWorkerStat>> sinkWorkersByCid =
            new ConcurrentHashMap<>();

    /**
     * Per-workflow sliding window of recent {@link SinkChunkRecord}
     * entries ( cap 1000 most recent ) , populated on every
     * {@code SinkChunkWritten} event . Powers the SINK drill-down
     * modal in oa-live ( "list of chunks loaded into DB" ) .
     */
    private static final int SINK_CHUNKS_WINDOW = 1000;
    private final ConcurrentMap<UUID, java.util.Deque<SinkChunkRecord>> sinkChunksByCid =
            new ConcurrentHashMap<>();

    /**
     * UUID du binaryfile source de l'import , publie par
     * {@link fr.inra.oresing.rest.data.DataService} avant le demarrage de
     * cascade . Utilise par {@link WorkflowMetadataCollector} pour enrichir
     * {@code workflow_log.metadata.binaryFileId} et par
     * {@link fr.inra.oresing.monitoring.integrity.IntegrityService} pour
     * compter les rows {@code referencevalue WHERE binaryfile = ?} et
     * detecter les imports incoherents .
     */
    private final ConcurrentMap<UUID, UUID> binaryFileIdByCid = new ConcurrentHashMap<>();

    /**
     * Phase de chargement final ( cascade emit -> finalize -> rollback ) .
     * Alimente le bloc CHARGEMENT FINAL d'oa-live ( separe la mesure du
     * debit cascade et du debit UPSERT/COPY final ) .
     */
    private final ConcurrentMap<UUID, FinalizePhaseSnapshot> finalizePhaseByCid =
            new ConcurrentHashMap<>();

    /**
     * Compteurs in-memory des rows ecrites en staging et en finale par
     * workflow . Mis a jour a chaud par les listeners cascade ( pas de
     * SQL count par poll ) . Approximation acceptable : si une rollback
     * survient , les compteurs ne sont pas decrementes ( frontend les
     * masque automatiquement quand phase = ROLLBACK_DONE ) .
     */
    private final ConcurrentMap<UUID, java.util.concurrent.atomic.AtomicLong>
            stagingRowsByCid = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, java.util.concurrent.atomic.AtomicLong>
            finalRowsByCid = new ConcurrentHashMap<>();

    /**
     * Sous-phase MERGE_FILE ( {@code MERGE_LOCAL} / {@code TEMP_LOAD} /
     * {@code UPSERT_FINAL} ) emise par {@link DataRepository#storeAll}
     * via {@link StoreAllPathSink} . Permet a la live view d'afficher
     * 3 progress bars distinctes au lieu de 2 ( bar A determinate ,
     * bar B indeterminate pendant TEMP_LOAD , bar C determinate ) .
     * Null pour les workflows DIRECT_COPY ( pas de sous-phase MERGE_FILE ) .
     */
    private final ConcurrentMap<UUID, String> mergeFilePhaseByCid =
            new ConcurrentHashMap<>();

    public void addStagingRows(UUID correlationId, long delta) {
        if (correlationId == null || delta <= 0) return;
        stagingRowsByCid.computeIfAbsent(correlationId,
                k -> new java.util.concurrent.atomic.AtomicLong()).addAndGet(delta);
    }

    public void addFinalRows(UUID correlationId, long delta) {
        if (correlationId == null || delta <= 0) return;
        finalRowsByCid.computeIfAbsent(correlationId,
                k -> new java.util.concurrent.atomic.AtomicLong()).addAndGet(delta);
    }

    /**
     * Remplace le compteur {@code finalRows} par la valeur authoritative
     * issue d'un {@code COUNT(*) FROM <app>.referencevalue WHERE binaryfile=?}
     * post-afterCommit . Appele par {@code CascadeImportPipeline.markCompleted}
     * AVANT {@link #finish(UUID)} pour que le dernier poll de
     * {@code WorkflowFinalizeBadge} voie la valeur exacte ( les compteurs
     * incrementaux pendant la phase finalize sont des approximations
     * alimentees par callbacks ; ils peuvent diverger legerement du count
     * DB reel a cause des batches UPSERT non encore committes ) .
     *
     * <p>Cf AUDIT 06-05-26 #1 .
     */
    public void setFinalRowsAuthoritative(UUID correlationId, long count) {
        if (correlationId == null || count < 0) return;
        finalRowsByCid.computeIfAbsent(correlationId,
                k -> new java.util.concurrent.atomic.AtomicLong()).set(count);
    }

    public long stagingRows(UUID correlationId) {
        var c = stagingRowsByCid.get(correlationId);
        return c == null ? 0L : c.get();
    }

    public long finalRows(UUID correlationId) {
        var c = finalRowsByCid.get(correlationId);
        return c == null ? 0L : c.get();
    }

    /**
     * Marque la sous-phase MERGE_FILE en cours pour ce workflow . Appelle
     * par {@link StoreAllPathSink#write} via le callback
     * {@code onPhaseChange} expose par {@code DataRepository.storeAll} .
     *
     * @param phase {@code MERGE_LOCAL} | {@code TEMP_LOAD} | {@code UPSERT_FINAL}
     */
    public void setMergeFilePhase(UUID correlationId, String phase) {
        if (correlationId == null || phase == null) return;
        mergeFilePhaseByCid.put(correlationId, phase);
    }

    /** @return sous-phase MERGE_FILE courante , ou empty pour DIRECT_COPY ou avant TEMP_LOAD . */
    public Optional<String> findMergeFilePhase(UUID correlationId) {
        return Optional.ofNullable(mergeFilePhaseByCid.get(correlationId));
    }

    public void initFinalizePhase(UUID correlationId, Instant startedAt) {
        if (correlationId == null) return;
        finalizePhaseByCid.put(correlationId, FinalizePhaseSnapshot.starting(startedAt));
    }

    public void markCascadeFinished(UUID correlationId, Instant at) {
        if (correlationId == null) return;
        finalizePhaseByCid.computeIfPresent(correlationId, (k, cur) -> cur.withCascadeFinished(at));
    }

    public void markFinalizeFinished(UUID correlationId, Instant at) {
        if (correlationId == null) return;
        finalizePhaseByCid.computeIfPresent(correlationId, (k, cur) -> cur.withFinalizeFinished(at));
    }

    public void markRollbackStarted(UUID correlationId, Instant at, long rowsBefore, String error) {
        if (correlationId == null) return;
        finalizePhaseByCid.computeIfPresent(correlationId,
                (k, cur) -> cur.withRollbackStarted(at, rowsBefore, error));
    }

    public void markRollbackFinished(UUID correlationId, Instant at) {
        if (correlationId == null) return;
        finalizePhaseByCid.computeIfPresent(correlationId, (k, cur) -> cur.withRollbackFinished(at));
    }

    public Optional<FinalizePhaseSnapshot> findFinalizePhase(UUID correlationId) {
        return Optional.ofNullable(finalizePhaseByCid.get(correlationId));
    }

    /** Publie le binaryfile source d'un workflow d'import . */
    public void setBinaryFileId(UUID correlationId, UUID binaryFileId) {
        if (correlationId == null || binaryFileId == null) return;
        binaryFileIdByCid.put(correlationId, binaryFileId);
    }

    /** Retourne le binaryfile source d'un workflow ou empty si non publie . */
    public Optional<UUID> findBinaryFileId(UUID correlationId) {
        return Optional.ofNullable(binaryFileIdByCid.get(correlationId));
    }

    /**
     * Subscribes this registry as a cascade listener at startup so that
     * onChunkStart / onChunkProgress / onChunkEnd events feed the
     * per-chunk drill-down.
     */
    @PostConstruct
    void wireCascadeListener() {
        WorkflowEventBus.getInstance().subscribe(this);
        log.info("WorkflowActiveRegistry subscribed to cascade WorkflowEventBus");
    }

    @PreDestroy
    void unwireCascadeListener() {
        WorkflowEventBus.getInstance().unsubscribe(this);
    }

    // ----------------------------------------------------------------
    //  Workflow-level state ( unchanged API )
    // ----------------------------------------------------------------

    /** Registers a workflow as started. Does nothing if already present. */
    public void start(WorkflowSnapshot snapshot) {
        byCorrelationId.putIfAbsent(snapshot.correlationId(), snapshot);
        log.debug("Workflow registered : {} / {}", snapshot.workflowType(), snapshot.correlationId());
    }

    /**
     * Updates progress on an existing entry. No-op if the correlationId
     * is not registered ( late update after finish , or racing ). Safe to
     * call concurrently from multiple workers.
     */
    public void update(
            UUID correlationId,
            long recordsProcessed,
            long recordsFailed,
            int chunksProcessed,
            Double progressPercentage,
            long bytesTotal) {

        WorkflowSnapshot snap = byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withProgress(
                        recordsProcessed, recordsFailed, chunksProcessed,
                        progressPercentage, bytesTotal));

        // Auto-bascule en phase FINALIZE_RUNNING des que cascade emit 100 %
        // ( recordsProcessed >= recordsTotal ) . Pour DIRECT_COPY le finalize
        // hook tourne dans cascade workflow.execute() ; sans ce hook la phase
        // resterait CASCADE_RUNNING jusqu'au commit final , et la duree
        // finalize serait egale a 0 ms . En marquant cascadeFinishedAt ici
        // on capture le bon delimiteur entre emit et UPSERT .
        if (snap != null && snap.recordsTotal() > 0
                && recordsProcessed >= snap.recordsTotal()) {
            finalizePhaseByCid.computeIfPresent(correlationId, (k, cur) ->
                    cur.cascadeFinishedAt() == null
                            ? cur.withCascadeFinished(java.time.Instant.now())
                            : cur);
        }
    }

    /**
     * Records the total number of records expected for a workflow once
     * known ( typically after the file has been counted ). Allows oa-live
     * to switch the progress bar from indeterminate to determinate.
     * No-op if the entry is not registered.
     */
    public void setRecordsTotal(UUID correlationId, long recordsTotal) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withRecordsTotal(recordsTotal));
    }

    /**
     * Records the resolved parallelism block ( source / transform / sink )
     * for a workflow once the executor has decided which thread pools to
     * use . Called by {@link fr.inra.oresing.workflow.cascade.CascadeImportPipeline}
     * right after the {@link fr.inrae.ore.cascade.model.workflow.WorkflowConfig}
     * is finalised . No-op if the entry is not registered .
     */
    public void setParallelism(UUID correlationId, ParallelismSnapshot parallelism) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withParallelism(parallelism));
    }

    /**
     * Records the resolved cascade strategy ( sinkStrategy ,
     * stagingStrategy , executionMode , streamingMode ,
     * directWriteParallel ) for a workflow . Called by
     * {@link fr.inra.oresing.workflow.cascade.CascadeImportPipeline}
     * right after the {@link fr.inrae.ore.cascade.model.workflow.WorkflowConfig}
     * is finalised . No-op if the entry is not registered .
     */
    public void setStrategy(UUID correlationId, StrategySnapshot strategy) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withStrategy(strategy));
    }

    /**
     * Records the import-pipeline config snapshot ( chunkSize , pools ,
     * staging , metrics , ... ) capturee au demarrage du workflow . Affiche
     * dans le Detail du workflow ( oa-live ) pour faciliter le debug
     * perf / config a posteriori . No-op si l'entree n'est pas enregistree .
     */
    public void setImportConfig(UUID correlationId, ImportConfigSnapshot config) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withImportConfig(config));
    }

    /**
     * Records the latest heartbeat timestamp emitted by {@code HeartbeatService}
     * during long-running phases ( finalize hook ) . Permet a oa-live de
     * distinguer "workflow vivant mais lent" de "workflow mort" via le pill
     * vert / orange / rouge selon l'age du heartbeat . No-op si l'entree
     * n'est plus enregistree ( workflow termine entre temps ) .
     */
    public void setLastHeartbeat(UUID correlationId, java.time.Instant heartbeatAt) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withLastHeartbeatAt(heartbeatAt));
    }

    /** Removes the entry from the registry once the workflow is over. */
    public void finish(UUID correlationId) {
        WorkflowSnapshot removed = byCorrelationId.remove(correlationId);
        chunksByCorrelationId.remove(correlationId);
        sourceWorkersByCid.remove(correlationId);
        sinkWorkersByCid.remove(correlationId);
        sinkChunksByCid.remove(correlationId);
        binaryFileIdByCid.remove(correlationId);
        finalizePhaseByCid.remove(correlationId);
        stagingRowsByCid.remove(correlationId);
        finalRowsByCid.remove(correlationId);
        mergeFilePhaseByCid.remove(correlationId);
        if (removed != null) {
            log.debug("Workflow unregistered : {} / {}",
                    removed.workflowType(), correlationId);
        }
    }

    /**
     * Replaces the workflow snapshot in-place ( phase update ) without
     * touching the chunks map. Useful for transitions like
     * UPLOADING -> CHUNKING -> PROCESSING -> LOADING_DB where the
     * caller wants to keep the live chunks data.
     */
    public void replace(WorkflowSnapshot snapshot) {
        byCorrelationId.put(snapshot.correlationId(), snapshot);
    }

    // ----------------------------------------------------------------
    //  Lookup ( injects chunks at read time )
    // ----------------------------------------------------------------

    /** Direct lookup , used by the detail endpoint to serve live data. */
    public Optional<WorkflowSnapshot> find(UUID correlationId) {
        WorkflowSnapshot s = byCorrelationId.get(correlationId);
        return Optional.ofNullable(s).map(this::injectChunks);
    }

    /**
     * Returns a snapshot of all active workflows , sorted by startTime DESC
     * ( most recent first ). Optionally filtered by user id - null means no
     * filter ( admin view ).
     */
    public List<WorkflowSnapshot> list(UUID userFilter) {
        Collection<WorkflowSnapshot> all = byCorrelationId.values();
        return all.stream()
                .filter(s -> userFilter == null || userFilter.equals(s.userId()))
                .sorted(Comparator.comparing(WorkflowSnapshot::startTime).reversed())
                .map(this::injectChunks)
                .collect(Collectors.toList());
    }

    /** Number of entries currently tracked. Mostly useful for tests + health. */
    public int size() {
        return byCorrelationId.size();
    }

    /**
     * Replaces {@code WorkflowSnapshot.chunks} with the live chunk state
     * tracked via cascade listeners . Called at every read so the chunks
     * field is always fresh . Also computes the per-worker aggregated view
     * ( {@link WorkerSnapshot} ) so the dashboard does not have to group
     * client-side .
     */
    private WorkflowSnapshot injectChunks(WorkflowSnapshot s) {
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(s.correlationId());
        List<ChunkSnapshot> sortedChunks = (chunks == null || chunks.isEmpty())
                ? List.of()
                : chunks.values().stream()
                        .sorted(Comparator.comparingInt(ChunkSnapshot::chunkIndex))
                        .toList();

        // 3 stages combined into a single ordered list ( SOURCE then
        // TRANSFORM then SINK ) so the dashboard renders them in
        // pipeline-natural order .
        List<WorkerSnapshot> workers = new java.util.ArrayList<>();
        workers.addAll(stageWorkers(sourceWorkersByCid.get(s.correlationId()), "SOURCE"));
        workers.addAll(aggregateTransformWorkers(sortedChunks));
        List<WorkerSnapshot> sinkWorkers = stageWorkers(
                sinkWorkersByCid.get(s.correlationId()), "SINK");
        // Placeholders : si cascade n'a pas encore emit d'event sink pour
        // ce workflow ( BUFFERED + ASYNC : sink ne demarre qu'apres tous
        // chunks transform emis ; OU MERGE_FILE : sink filesystem inline
        // sans events ) , on synthetise N entries SINK selon le parallelism
        // configure ( {@code s.parallelism().sink()} ) . L'admin voit
        // immediatement les 4 sinks pre-vus en attente plutot qu'1 ligne
        // generique ambigue .
        int sinkParallelism = s.parallelism() != null ? s.parallelism().sink() : 1;
        java.util.Set<String> existingNames = sinkWorkers.stream()
                .map(WorkerSnapshot::name).collect(java.util.stream.Collectors.toSet());
        for (int i = 1; i <= sinkParallelism; i++) {
            String name = "sink-" + i;
            if (!existingNames.contains(name)) {
                sinkWorkers = new java.util.ArrayList<>(sinkWorkers);
                sinkWorkers.add(new WorkerSnapshot(
                        "SINK", name, "IDLE", null,
                        0L, 0L, null, 0, null, null, null));
            }
        }
        // Trier par nom pour stabilite affichage ( sink-1 , sink-2 , ... ) .
        sinkWorkers = sinkWorkers.stream()
                .sorted(Comparator.comparing(WorkerSnapshot::name))
                .toList();
        workers.addAll(sinkWorkers);

        // Sliding window des sink chunks - alimente la modal SINK
        // ( drill-down "fichiers charges en base" ) .
        java.util.Deque<SinkChunkRecord> sinkRecords = sinkChunksByCid.get(s.correlationId());
        List<SinkChunkRecord> sinkChunksList = (sinkRecords == null || sinkRecords.isEmpty())
                ? List.of()
                : List.copyOf(sinkRecords);

        // Override le parallelism figé par la taille pool live , de sorte
        // que les changements de pool size faits via l'edition live de
        // configuration soient visibles immediatement dans le header
        // oa-live . sinkParallelism reste celui du workflow ( forcé à 1
        // par PER_CONNECTION_TEMP ) car le pool sink peut etre 8 mais le
        // workflow lui n'utilise qu'1 thread - afficher 8 serait
        // trompeur .
        WorkflowSnapshot withLiveParallelism = s;
        if (poolReloader != null && s.parallelism() != null) {
            int liveSource    = livePoolSize(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.SOURCE,    s.parallelism().source());
            int liveTransform = livePoolSize(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.TRANSFORM, s.parallelism().transform());
            // sink reste figé : voir commentaire ci-dessus
            int sinkConfigured = s.parallelism().sink();
            if (liveSource != s.parallelism().source() || liveTransform != s.parallelism().transform()) {
                withLiveParallelism = s.withParallelism(new ParallelismSnapshot(
                        liveSource, liveTransform, sinkConfigured));
            }
        }

        return withLiveParallelism.withChunks(sortedChunks)
                .withWorkers(List.copyOf(workers))
                .withSinkChunks(sinkChunksList);
    }

    private int livePoolSize(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage stage,
                             int fallback) {
        try {
            var snap = poolReloader.snapshot(stage);
            int n = snap == null ? -1 : snap.corePoolSize();
            return n > 0 ? n : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /**
     * Aggregates {@link ChunkSnapshot} entries into one
     * {@link WorkerSnapshot} per distinct {@code workerName} for the
     * TRANSFORM stage . Renamed in 1.9.1 from {@code aggregateWorkers}
     * to avoid confusion with the new source / sink aggregations .
     */
    private static List<WorkerSnapshot> aggregateTransformWorkers(List<ChunkSnapshot> chunks) {
        Map<String, List<ChunkSnapshot>> byWorker = new LinkedHashMap<>();
        for (ChunkSnapshot c : chunks) {
            String name = c.workerName();
            if (name == null || name.isBlank()) continue;
            byWorker.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(c);
        }
        return byWorker.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildWorkerSnapshot(e.getKey(), e.getValue()))
                .toList();
    }

    private static WorkerSnapshot buildWorkerSnapshot(String name, List<ChunkSnapshot> entries) {
        ChunkSnapshot running = entries.stream()
                .filter(c -> "RUNNING".equals(c.status()))
                .findFirst().orElse(null);

        ChunkSnapshot lastFinished = entries.stream()
                .filter(c -> c.endTime() != null)
                .max(Comparator.comparing(ChunkSnapshot::endTime))
                .orElse(null);

        Duration lastDuration = null;
        if (lastFinished != null && lastFinished.startTime() != null) {
            lastDuration = Duration.between(lastFinished.startTime(), lastFinished.endTime());
        }

        // Rolling average over the last 10 finished chunks of this worker .
        // Smooths the per-tick jitter when chunks complete fast , while still
        // tracking long-term throughput drifts ( e.g. degradation when the
        // DB starts thrashing ) .
        List<Duration> recentDurations = entries.stream()
                .filter(c -> c.endTime() != null && c.startTime() != null)
                .sorted(Comparator.comparing(ChunkSnapshot::endTime).reversed())
                .limit(10)
                .map(c -> Duration.between(c.startTime(), c.endTime()))
                .toList();
        Duration avgDuration = null;
        if (!recentDurations.isEmpty()) {
            long avgNanos = (long) recentDurations.stream()
                    .mapToLong(Duration::toNanos)
                    .average()
                    .orElse(0d);
            avgDuration = Duration.ofNanos(avgNanos);
        }

        Instant lastActivity = entries.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.startTime(), c.endTime()))
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String status        = running != null ? "RUNNING" : "IDLE";
        Integer currentChunk = running != null ? running.chunkIndex() : null;
        long curProcessed    = running != null ? running.recordsProcessed() : 0L;
        long curTotal        = running != null ? running.recordsTotal()     : 0L;
        Double curPct        = running != null ? running.progressPercentage() : null;

        return new WorkerSnapshot(
                "TRANSFORM",
                name,
                status,
                currentChunk,
                curProcessed,
                curTotal,
                curPct,
                entries.size(),
                lastDuration,
                avgDuration,
                lastActivity);
    }

    /**
     * Builds {@link WorkerSnapshot} list for SOURCE / SINK stages from
     * the lightweight {@link StageWorkerStat} accumulators . Sorted by
     * worker name so the UI grid stays stable across refreshes .
     */
    private static List<WorkerSnapshot> stageWorkers(
            ConcurrentMap<String, StageWorkerStat> stats, String stage) {
        if (stats == null || stats.isEmpty()) return List.of();
        return stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().toSnapshot(stage, e.getKey()))
                .toList();
    }

    /**
     * Mutable per-worker accumulator for SOURCE / SINK stages . Updated
     * from listener callbacks ; converted to an immutable
     * {@link WorkerSnapshot} at read time .
     */
    static final class StageWorkerStat {
        volatile String  status = "IDLE";
        volatile Integer currentChunk;
        volatile long    currentRecordsProcessed;
        volatile long    currentRecordsTotal;
        volatile int     chunksDone;
        volatile Long    lastDurationMs;
        volatile Instant lastActivity;
        final java.util.Deque<Long> recentDurationsMs = new java.util.ArrayDeque<>();

        synchronized void recordEnd(Long durationMs) {
            chunksDone++;
            status = "IDLE";
            currentChunk = null;
            currentRecordsProcessed = 0L;
            currentRecordsTotal = 0L;
            if (durationMs != null) {
                lastDurationMs = durationMs;
                recentDurationsMs.addLast(durationMs);
                while (recentDurationsMs.size() > 10) recentDurationsMs.removeFirst();
            }
        }

        synchronized WorkerSnapshot toSnapshot(String stage, String name) {
            Long avgMs = recentDurationsMs.isEmpty()
                    ? null
                    : (long) recentDurationsMs.stream().mapToLong(Long::longValue).average().orElse(0d);
            Double pct = (currentRecordsTotal > 0)
                    ? Math.min(100d, (currentRecordsProcessed * 100d) / currentRecordsTotal)
                    : null;
            return new WorkerSnapshot(
                    stage, name, status, currentChunk,
                    currentRecordsProcessed, currentRecordsTotal, pct,
                    chunksDone,
                    lastDurationMs == null ? null : Duration.ofMillis(lastDurationMs),
                    avgMs == null ? null : Duration.ofMillis(avgMs),
                    lastActivity);
        }
    }

    // ----------------------------------------------------------------
    //  Cascade WorkflowListener implementation
    // ----------------------------------------------------------------

    @Override
    public void onChunkStart(WorkflowEvents.ChunkStartEvent e) {
        log.debug("[{}] onChunkStart : chunk #{} expects {} records on {}",
                e.correlationId(), e.chunkIndex(), e.recordsExpected(), e.workerName());
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) {
            log.warn("onChunkStart : correlationId '{}' is not a valid UUID , chunk skipped",
                    e.correlationId());
            return;
        }
        chunksByCorrelationId
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .put(e.chunkIndex(), new ChunkSnapshot(
                        e.chunkIndex(),
                        "RUNNING",
                        0L,
                        e.recordsExpected(),
                        e.workerName(),
                        e.startTime(),
                        null,
                        null));
    }

    @Override
    public void onChunkProgress(WorkflowEvents.ChunkProgressEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(corrId);
        if (chunks == null) return;
        chunks.computeIfPresent(e.chunkIndex(), (idx, cur) ->
                cur.withProgress(e.totalProcessedSoFar()));
    }

    @Override
    public void onChunkEnd(WorkflowEvents.ChunkEndEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(corrId);
        if (chunks == null) return;
        String status = switch (e.status()) {
            case SUCCESS  -> "COMPLETED";
            case FAILED   -> "FAILED";
            case CANCELLED -> "CANCELLED";
            default       -> e.status().name();
        };
        chunks.computeIfPresent(e.chunkIndex(), (idx, cur) ->
                cur.withEnd(status, e.recordsProcessed(), e.endTime(), e.errorMessage()));
    }

    // ----------------------------------------------------------------
    //  Workflow lifecycle ( cascade ) - reset stuck workers
    // ----------------------------------------------------------------

    /**
     * Resets every still-RUNNING SOURCE / SINK worker to IDLE when
     * cascade signals the workflow is over .
     *
     * <p>Background : {@code SourceInstrumentation} ( cascade 1.9.0+ )
     * fires {@code SourceFetchStartEvent} before every spliterator
     * {@code tryAdvance()} ; when the source is exhausted the
     * underlying advance returns {@code false} without invoking the
     * consumer , so the matching {@code SourceChunkEmittedEvent} is
     * never fired and the source worker stays stuck in RUNNING . This
     * handler closes the loop : at workflow end , any stale RUNNING
     * worker is forced to IDLE so the dashboard does not display a
     * "phantom" source / sink activity during the post-workflow
     * {@code CHARGEMENT_DB} phase ( e.g. {@code storeAll} on
     * MERGE_FILE ) .
     */
    @Override
    public void onWorkflowEnd(WorkflowEvents.WorkflowEndEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        ConcurrentMap<String, StageWorkerStat> sources = sourceWorkersByCid.get(corrId);
        if (sources != null) {
            sources.values().forEach(w -> { w.status = "IDLE"; w.currentChunk = null; });
        }
        ConcurrentMap<String, StageWorkerStat> sinks = sinkWorkersByCid.get(corrId);
        if (sinks != null) {
            sinks.values().forEach(w -> { w.status = "IDLE"; w.currentChunk = null; });
        }
    }

    /**
     * Stale-RUNNING reset on heartbeat ( cascade fires {@code PoolHeartbeatEvent}
     * every 1 s ) . Closes the gap between {@code SourceFetchStart} ( fired
     * by {@code SourceInstrumentation} before every spliterator
     * {@code tryAdvance()} ) and {@code SourceChunkEmitted} ( NOT fired
     * when advance returns {@code false} on EOF ) : the source worker would
     * otherwise stay {@code RUNNING} during the entire post-EOF finalize /
     * CHARGEMENT_DB phase , long after it actually finished emitting .
     *
     * <p>Heuristic : a worker marked {@code RUNNING} whose last activity
     * is older than {@link #STALE_RUNNING_RESET_MS} ms is forced back to
     * {@code IDLE} . The threshold is well above the per-chunk timing of
     * SOURCE / SINK in nominal mode ( fast sinks 12 ms , source fetch
     * 50-200 ms ) so it does not fight the per-event tracker .
     *
     * <p>Applies to SOURCE and SINK only ; TRANSFORM is event-driven via
     * the {@code ChunkStart} / {@code ChunkEnd} pair which always fires .
     */
    @Override
    public void onPoolHeartbeat(WorkflowEvents.PoolHeartbeatEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        long nowMs = (e.time() != null ? e.time() : Instant.now()).toEpochMilli();
        resetStaleRunning(sourceWorkersByCid.get(corrId), nowMs);
        resetStaleRunning(sinkWorkersByCid.get(corrId),   nowMs);
    }

    /** Workers idle if no event seen for longer than this . */
    private static final long STALE_RUNNING_RESET_MS = 2_000L;

    private static void resetStaleRunning(ConcurrentMap<String, StageWorkerStat> workers,
                                          long nowMs) {
        if (workers == null) return;
        for (StageWorkerStat w : workers.values()) {
            if (!"RUNNING".equals(w.status)) continue;
            if (w.lastActivity == null) continue;
            if (nowMs - w.lastActivity.toEpochMilli() > STALE_RUNNING_RESET_MS) {
                w.status = "IDLE";
                w.currentChunk = null;
                w.currentRecordsProcessed = 0L;
                w.currentRecordsTotal = 0L;
            }
        }
    }

    // ----------------------------------------------------------------
    //  SOURCE stage listener callbacks ( cascade 1.9.0 events )
    // ----------------------------------------------------------------

    @Override
    public void onSourceFetchStart(WorkflowEvents.SourceFetchStartEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sourceWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        w.status = "RUNNING";
        w.lastActivity = e.time();
    }

    @Override
    public void onSourceChunkEmitted(WorkflowEvents.SourceChunkEmittedEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sourceWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        // Source has no chunk-end timing , so we just bump the counter
        // and reset to IDLE . durationMs is unknown for the source path .
        w.recordEnd(null);
        w.lastActivity = e.time();
    }

    // ----------------------------------------------------------------
    //  SINK stage listener callbacks ( cascade 1.9.0 events )
    // ----------------------------------------------------------------

    @Override
    public void onSinkChunkAccepted(WorkflowEvents.SinkChunkAcceptedEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sinkWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        w.status = "RUNNING";
        w.currentChunk = e.chunkIndex();
        w.lastActivity = e.time();
    }

    @Override
    public void onSinkChunkWritten(WorkflowEvents.SinkChunkWrittenEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sinkWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        Long durMs = e.duration() != null ? e.duration().toMillis() : null;
        w.recordEnd(durMs);
        w.lastActivity = e.endTime() != null ? e.endTime() : Instant.now();

        // Increment compteur staging in-memory : sink ecrit chunk -> staging
        // table ( DIRECT_COPY ) ou chunk file ( MERGE_FILE qui sera ensuite
        // mergee dans merged.csv puis chargee via storeAll ) . Approximation
        // suffisante pour l'UI temps reel ( pas de SQL count par poll ) .
        if (e.recordsWritten() > 0) {
            addStagingRows(corrId, e.recordsWritten());
        }

        // Append to the sliding window for the SINK drill-down modal .
        // Skip the synthetic chunkIndex=-1 entries ( writeFromDisk path )
        // because the modal lists per-chunk loads only ; users see the
        // batch-from-disk on the workers row counter instead .
        if (e.chunkIndex() < 0) return;
        java.util.Deque<SinkChunkRecord> records = sinkChunksByCid
                .computeIfAbsent(corrId, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        records.addLast(new SinkChunkRecord(
                e.chunkIndex(),
                e.workerName(),
                e.status() != null ? e.status().name() : "UNKNOWN",
                durMs != null ? durMs : 0L,
                e.endTime() != null ? e.endTime() : Instant.now(),
                e.errorMessage()));
        // Cap the deque ( cheap , bounded write rate ) .
        while (records.size() > SINK_CHUNKS_WINDOW) {
            records.pollFirst();
        }
    }

    private static UUID safeUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
