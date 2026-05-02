package fr.inra.oresing.rest.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.ImportRateLimiter;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import fr.inrae.ore.cascade.core.execution.WorkflowPoolRegistry;
import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Backs the three /api/dashboard/workflows/* endpoints.
 *
 * <p>Authorisation model :
 * <ul>
 *   <li>admin users ( role {@code openAdomAdmin} ) see every workflow</li>
 *   <li>any other authenticated user sees only their own</li>
 * </ul>
 * Filtering is pushed down to SQL ( WHERE user_id = :userId ) or to the
 * registry iteration ; we never return data then strip it in the controller.
 *
 * <p>Phase 3 dashboard ( issue #62 ).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final WorkflowActiveRegistry registry;
    private final fr.inra.oresing.workflow.cascade.pipeline.PipelineRegistry pipelineRegistry;

    /**
     * Optional : PoolReloader peut etre absent en mode test sans cascade
     * WorkflowPoolRegistry . Field injection volontaire car
     * {@link lombok.RequiredArgsConstructor} generera un ctor obligatoire
     * sur les final fields ; ici on veut required=false .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.config.PoolReloader poolReloader;
    private final fr.inra.oresing.monitoring.session.UserSessionRegistry sessionRegistry;
    private final fr.inra.oresing.monitoring.session.UserSessionLogRepository sessionLogRepository;
    private final fr.inra.oresing.monitoring.session.UserSessionLogWriter sessionLogWriter;
    private final fr.inra.oresing.monitoring.session.JwtBlacklistRegistry jwtBlacklist;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuthenticationService authenticationService;
    private final ImportProperties importProperties;
    private final ImportRateLimiter importRateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cascade.extraction.max-concurrent-per-user:-1}")
    private int maxConcurrentExtractionsPerUser;

    @Value("${cascade.extraction.acquire-timeout-seconds:0}")
    private long extractionAcquireTimeoutSeconds;

    /**
     * Lit la version réelle de la lib cascade depuis le MANIFEST de son
     * JAR ( {@code Implementation-Version} renseigné par Maven au build ).
     * Permet d'éviter le piège "cascade.version" Spring property qu'il
     * fallait synchroniser à la main avec le pom backend ; ici la valeur
     * affichée est toujours celle de la classe effectivement chargée.
     */
    private static String resolveCascadeVersion() {
        String fromManifest = fr.inrae.ore.cascade.model.workflow.WorkflowConfig
                .class.getPackage().getImplementationVersion();
        return fromManifest != null ? fromManifest : "unknown";
    }

    // ---------------------------------------------------------------- //
    //  in-progress                                                     //
    // ---------------------------------------------------------------- //

    public List<DashboardWorkflowDTO> listInProgress() {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        UUID filter = me.isOpenAdomAdmin() ? null : me.userId();
        return registry.list(filter).stream()
                .map(DashboardWorkflowDTO::fromSnapshot)
                .toList();
    }

    // ---------------------------------------------------------------- //
    //  history                                                         //
    // ---------------------------------------------------------------- //

    public DashboardWorkflowDTO.Page listHistory(
            Integer limit, Integer offset,
            String type, String status,
            String app, String user) {

        CurrentUserRoles me = authenticationService.getCurrentUserRoles();

        int l = clamp(Optional.ofNullable(limit).orElse(DEFAULT_LIMIT), 1, MAX_LIMIT);
        int o = Math.max(0, Optional.ofNullable(offset).orElse(0));

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource p = new MapSqlParameterSource();

        if (!me.isOpenAdomAdmin()) {
            where.append(" AND user_id = :userId ");
            p.addValue("userId", me.userId());
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND workflow_type = :type ");
            p.addValue("type", type);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = :status ");
            p.addValue("status", status);
        }
        if (app != null && !app.isBlank()) {
            where.append(" AND application_name ILIKE :app ");
            p.addValue("app", "%" + app + "%");
        }
        if (user != null && !user.isBlank()) {
            where.append(" AND ( user_login ILIKE :user OR user_id::text ILIKE :user ) ");
            p.addValue("user", "%" + user + "%");
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM oa_audit.workflow_log " + where, p, Long.class);

        p.addValue("limit", l);
        p.addValue("offset", o);

        List<DashboardWorkflowDTO> items = jdbc.query(
                "SELECT correlation_id, workflow_type, user_id, user_login, " +
                "       application_name, data_type, resource_name, " +
                "       start_time, end_time, duration_ms, status, " +
                "       records_processed, records_failed, chunks_processed, " +
                "       progress_percentage, bytes_total " +
                "  FROM oa_audit.workflow_log " +
                where +
                " ORDER BY start_time DESC " +
                " LIMIT :limit OFFSET :offset ",
                p, this::mapSummary);

        return new DashboardWorkflowDTO.Page(items, total == null ? 0L : total, l, o);
    }

    // ---------------------------------------------------------------- //
    //  detail                                                          //
    // ---------------------------------------------------------------- //

    public Optional<DashboardWorkflowDTO.Detail> findDetail(UUID correlationId) {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        boolean admin = me.isOpenAdomAdmin();
        UUID myUserId = me.userId();

        // 1) try the in-memory registry ( still running )
        Optional<WorkflowSnapshot> live = registry.find(correlationId);
        if (live.isPresent()) {
            WorkflowSnapshot s = live.get();
            if (!admin && !s.userId().equals(myUserId)) {
                return Optional.empty();                         // treat as 404
            }
            DashboardWorkflowDTO summary = DashboardWorkflowDTO.fromSnapshot(s);
            return Optional.of(new DashboardWorkflowDTO.Detail(
                    summary, null, s.errors(), Map.of()));
        }

        // 2) fallback on oa_audit.workflow_log ( finished )
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cid", correlationId);
        List<DashboardWorkflowDTO.Detail> rows = jdbc.query(
                "SELECT correlation_id, workflow_type, user_id, user_login, " +
                "       application_name, data_type, resource_name, " +
                "       start_time, end_time, duration_ms, status, " +
                "       records_processed, records_failed, chunks_processed, " +
                "       progress_percentage, bytes_total, " +
                "       errors::text AS errors_json, fatal_error, " +
                "       metadata::text AS metadata_json " +
                "  FROM oa_audit.workflow_log " +
                " WHERE correlation_id = :cid ",
                p, this::mapDetail);

        if (rows.isEmpty()) return Optional.empty();
        DashboardWorkflowDTO.Detail d = rows.getFirst();
        if (!admin && !d.summary().userId().equals(myUserId)) {
            return Optional.empty();                             // 404 , not 403 , to avoid id enumeration
        }
        return Optional.of(d);
    }

    // ---------------------------------------------------------------- //
    //  pipeline ( cascade 1.9.0 )                                      //
    // ---------------------------------------------------------------- //

    /**
     * Returns the live cascade pipeline snapshot for a workflow
     * ( source / transform / sink workers + queues + recent events ) .
     * Same row-level authorisation as {@link #findDetail} : admin sees
     * everything , non-admin only their own workflows ; unmatched ids
     * return empty so the controller emits a 404 .
     */
    // ---------------------------------------------------------------- //
    //  sessions ( admin only )                                         //
    // ---------------------------------------------------------------- //

    /**
     * Liste in-memory des sessions ACTIVE . Admin only ; les non-admin
     * recoivent {@link AccessDeniedException} ( 403 ) .
     */
    public List<SessionDTO> listActiveSessions() {
        requireAdmin();
        java.time.Instant now = java.time.Instant.now();
        return sessionRegistry.listActive(now).stream()
                .map(s -> SessionDTO.fromSession(s, now))
                .toList();
    }

    /**
     * Marque une session active comme DISCONNECTED ( end_reason = KICK )
     * dans le registry in-memory et la persiste dans
     * {@code oa_audit.user_session_log} .
     *
     * <p><b>Note importante</b> : cette action est dashboard-side
     * uniquement . Le JWT du user reste techniquement valide jusqu'a
     * son expiration naturelle ; le user ne sera pas force-deconnecte
     * de l'application . Cette feature sert principalement au
     * housekeeping admin du dashboard ( ex : nettoyer une session
     * orpheline ) . Pour une vraie revocation de token , voir une
     * future implementation de blocklist JWT .
     *
     * @throws AccessDeniedException si l'appelant n'est pas admin
     * @throws java.util.NoSuchElementException si la session n'existe
     *         pas ou est deja terminee ( controller -> 404 )
     */
    public void disconnectSession(UUID sessionId) {
        requireAdmin();
        java.time.Instant now = java.time.Instant.now();
        fr.inra.oresing.monitoring.session.SessionInfo finished = sessionRegistry
                .finish(sessionId,
                        fr.inra.oresing.monitoring.session.SessionInfo.END_KICK,
                        now)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Session not found or already disconnected : " + sessionId));
        // Revocation effective : on inscrit le hash du JWT remis au login
        // dans la blacklist . Le AuthorizationFilter rejette les requetes
        // ulterieures avec 401 TOKEN_REVOKED -> le frontend ( interceptor
        // axios global ) redirige vers la page de login .
        if (finished.jwtTokenHash() != null) {
            jwtBlacklist.add(
                    finished.jwtTokenHash(),
                    finished.userId(),
                    finished.userLogin(),
                    finished.sessionId(),
                    now,
                    finished.expiresAt());
        } else {
            log.warn("disconnectSession : session {} has no jwtTokenHash , kick is cosmetic only "
                    + "( pre-blacklist session ; user can keep using the API until JWT TTL )",
                    sessionId);
        }
        sessionLogWriter.logAsync(
                fr.inra.oresing.monitoring.session.UserSessionLogEntry.fromSession(finished));
    }

    /**
     * Liste des entrees blacklist . Admin only ( {@code openAdomAdmin} ) .
     */
    public java.util.List<fr.inra.oresing.monitoring.session.JwtBlacklistRegistry.Entry> listBlacklist() {
        requireAdmin();
        return jwtBlacklist.list();
    }

    /**
     * Retire une entree blacklist ( admin annule un kick par erreur ) .
     * Renvoie {@code true} si l'entree existait et a ete supprimee .
     */
    public boolean removeBlacklistEntry(String tokenHash) {
        requireAdmin();
        return jwtBlacklist.remove(tokenHash);
    }

    /** Purge entiere de la blacklist par un admin . Renvoie le nombre d'entrees vidées . */
    public int clearBlacklist() {
        requireAdmin();
        return jwtBlacklist.clear();
    }

    /**
     * Pagination sur oa_audit.user_session_log . Admin only .
     */
    public SessionDTO.Page listSessionsHistory(Integer limit, Integer offset,
                                               String userLoginLike, String endReason) {
        requireAdmin();
        int l = clamp(Optional.ofNullable(limit).orElse(DEFAULT_LIMIT), 1, MAX_LIMIT);
        int o = Math.max(0, Optional.ofNullable(offset).orElse(0));
        java.util.List<fr.inra.oresing.monitoring.session.UserSessionLogEntry> rows =
                sessionLogRepository.findHistory(null, userLoginLike, endReason, l, o);
        long total = sessionLogRepository.count(null, userLoginLike, endReason);
        List<SessionDTO> items = rows.stream().map(SessionDTO::fromLogEntry).toList();
        return new SessionDTO.Page(items, total, l, o);
    }

    private void requireAdmin() {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        if (!me.isOpenAdomAdmin()) {
            throw new AccessDeniedException("Reserved to openAdomAdmin users");
        }
    }

    public Optional<PipelineDTO> pipeline(UUID correlationId) {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        boolean admin = me.isOpenAdomAdmin();
        UUID myUserId = me.userId();

        // The pipeline view is live-only : check the workflow is still
        // in the active registry and that the caller may see it .
        Optional<WorkflowSnapshot> live = registry.find(correlationId);
        if (live.isEmpty()) return Optional.empty();
        if (!admin && !live.get().userId().equals(myUserId)) {
            return Optional.empty();
        }
        // Enrichit le snapshot cascade avec la queue du pool SOURCE
        // ( CASCADE_POOL_SOURCE_QUEUE ) , que le snapshot cascade ne porte
        // pas ( il n'expose que les inboxes inter-stages transform / sink ) .
        // PoolReloader peut etre absent en mode test ; on garde un null
        // qui se materialise en sourceInbox=null cote DTO .
        fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot sourcePool = poolSnap(
                fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.SOURCE);
        fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot transformPool = poolSnap(
                fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.TRANSFORM);
        fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot sinkPool = poolSnap(
                fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.SINK);
        return pipelineRegistry.snapshot(correlationId)
                .map(s -> PipelineDTO.from(s, sourcePool, transformPool, sinkPool));
    }

    /**
     * Lookup PoolSnapshot best-effort : retourne null si le PoolReloader
     * est absent ( mode test ) ou si la query echoue ( pool pas encore
     * initialise ) . Le caller ( PipelineDTO.from ) gere null comme une
     * absence de fallback ( pas de placeholder workers ) .
     */
    private fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot poolSnap(
            fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage stage) {
        if (poolReloader == null) return null;
        try {
            return poolReloader.snapshot(stage);
        } catch (RuntimeException ex) {
            log.debug("pipeline : pool snapshot {} failed : {}", stage, ex.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- //
    //  finalize-progress  ( bloc CHARGEMENT FINAL )                    //
    // ---------------------------------------------------------------- //

    private static final java.util.regex.Pattern SAFE_IDENT =
            java.util.regex.Pattern.compile("^[a-z_][a-z0-9_]*$");

    /**
     * Snapshot temps reel du bloc CHARGEMENT FINAL pour un workflow .
     * Decompose la duree totale en {@code cascade} ( emit chunks ) +
     * {@code finalize} ( UPSERT staging->final ou COPY merged.csv->final )
     * + {@code rollback} . Le frontend poll cet endpoint pendant que
     * workflow phase != COMPLETED / ROLLBACK_DONE pour animer le bloc .
     */
    /**
     * Agregat global des workflows actifs en phase CHARGEMENT FINAL .
     * Consomme par {@code LiveFinalizeAggregate} ( bloc en tete de
     * page Live ) pour donner une vue infrastructure : combien de
     * workflows en finalize / rollback , progress cumule , debit total .
     *
     * <p>Auth : admin = tous workflows , user = ses propres workflows
     * uniquement ( meme regle que {@link #listInProgress} ) .
     */
    /**
     * Snapshot des 4 pools cascade pour le rendu Pipeline live en idle .
     * Lit {@code PoolReloader.snapshot(stage)} pour chaque stage ; null
     * silencieux si pool indisponible ( mode test ) -> DTO avec parallelism=0 .
     */
    public PipelinePoolsDTO cascadePools() {
        var src   = poolSnap(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.SOURCE);
        var trans = poolSnap(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.TRANSFORM);
        var sink  = poolSnap(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.SINK);
        var ord   = poolSnap(fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.ORDERING);
        return new PipelinePoolsDTO(
                PipelinePoolsDTO.PoolDTO.from(src),
                PipelinePoolsDTO.PoolDTO.from(trans),
                PipelinePoolsDTO.PoolDTO.from(sink),
                PipelinePoolsDTO.PoolDTO.from(ord));
    }

    public FinalizeAggregateDTO finalizeAggregate() {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        UUID filter = me.isOpenAdomAdmin() ? null : me.userId();

        int nbActive = 0;
        int nbFinalize = 0;
        int nbRollback = 0;
        int nbCompleted = 0;
        long expectedSum = 0L;
        long finalSum = 0L;
        long stagingSum = 0L;
        long throughputSum = 0L;

        for (WorkflowSnapshot snap : registry.list(filter)) {
            nbActive++;
            FinalizeProgressDTO p = finalizeProgress(snap.correlationId()).orElse(null);
            if (p == null) continue;
            switch (p.phase()) {
                case "FINALIZE_RUNNING"     -> nbFinalize++;
                case "ROLLBACK_IN_PROGRESS",
                     "ROLLBACK_DONE"        -> nbRollback++;
                case "COMPLETED"            -> nbCompleted++;
                default                     -> { /* CASCADE_RUNNING : compte juste dans nbActive */ }
            }
            expectedSum  += Math.max(0, p.expectedTotal());
            finalSum     += Math.max(0, p.finalCount());
            if (p.stagingRemaining() >= 0) stagingSum += p.stagingRemaining();
            throughputSum += Math.max(0, p.finalizeThroughput());
        }

        return new FinalizeAggregateDTO(
                nbActive, nbFinalize, nbRollback, nbCompleted,
                expectedSum, finalSum, stagingSum, throughputSum);
    }

    public Optional<FinalizeProgressDTO> finalizeProgress(UUID correlationId) {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        boolean admin = me.isOpenAdomAdmin();
        UUID myUserId = me.userId();

        Optional<WorkflowSnapshot> live = registry.find(correlationId);
        if (live.isEmpty()) return Optional.empty();
        WorkflowSnapshot snap = live.get();
        if (!admin && !snap.userId().equals(myUserId)) {
            return Optional.empty();
        }

        fr.inra.oresing.workflow.cascade.history.FinalizePhaseSnapshot phase =
                registry.findFinalizePhase(correlationId).orElse(null);
        UUID binaryFileId = registry.findBinaryFileId(correlationId).orElse(null);
        fr.inra.oresing.workflow.cascade.history.StrategySnapshot strategy = snap.strategy();

        long expectedTotal = snap.recordsTotal() > 0 ? snap.recordsTotal() : snap.recordsProcessed();

        // Final count : SELECT count referencevalue WHERE binaryfile = ?
        long finalCount = -1L;
        String appName = snap.applicationName();
        if (binaryFileId != null && appName != null && SAFE_IDENT.matcher(appName).matches()) {
            try {
                String sql = "SELECT COUNT(*) FROM \"" + appName + "\".referencevalue WHERE binaryfile = :bf";
                Long n = jdbc.queryForObject(sql,
                        new MapSqlParameterSource("bf", binaryFileId), Long.class);
                finalCount = n != null ? n : 0L;
            } catch (RuntimeException ex) {
                log.debug("finalizeProgress : count referencevalue failed for {}.{} : {}",
                        appName, correlationId, ex.getMessage());
            }
        }

        // Staging remaining : SHARED_UNLOGGED uniquement ( PER_CONNECTION_TEMP
        // est invisible cross-conn par design ) .
        long stagingRemaining = -1L;
        if (strategy != null && "SHARED_UNLOGGED".equals(strategy.stagingStrategy())) {
            try {
                Long n = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_staging.referencevalue_import_shared WHERE correlation_id = :cid",
                        new MapSqlParameterSource("cid", correlationId), Long.class);
                stagingRemaining = n != null ? n : 0L;
            } catch (RuntimeException ex) {
                log.debug("finalizeProgress : staging count failed for {} : {}", correlationId, ex.getMessage());
            }
        }

        Instant now = Instant.now();
        Instant cascadeStart = phase != null && phase.cascadeStartedAt() != null
                ? phase.cascadeStartedAt() : snap.startTime();
        Instant cascadeEnd = phase != null ? phase.cascadeFinishedAt() : null;
        Instant finalizeStart = phase != null ? phase.finalizeStartedAt() : null;
        Instant finalizeEnd = phase != null ? phase.finalizeFinishedAt() : null;
        Instant rollbackStart = phase != null ? phase.rollbackStartedAt() : null;
        Instant rollbackEnd = phase != null ? phase.rollbackFinishedAt() : null;

        long cascadeDur = cascadeStart == null ? 0L
                : Duration.between(cascadeStart,
                        cascadeEnd != null ? cascadeEnd : now).toMillis();
        long finalizeDur = finalizeStart == null ? 0L
                : Duration.between(finalizeStart,
                        finalizeEnd != null ? finalizeEnd : now).toMillis();
        long rollbackDur = rollbackStart == null ? 0L
                : Duration.between(rollbackStart,
                        rollbackEnd != null ? rollbackEnd : now).toMillis();

        long cascadeTput = cascadeDur > 0
                ? (snap.recordsProcessed() * 1000L / cascadeDur) : 0L;
        long finalizeTput = (finalizeDur > 0 && finalCount > 0)
                ? (finalCount * 1000L / finalizeDur) : 0L;

        String phaseName = phase != null ? phase.phase()
                : fr.inra.oresing.workflow.cascade.history.FinalizePhaseSnapshot.PHASE_CASCADE_RUNNING;

        // Compteurs in-memory ( pas de SQL count par poll ) maintenus
        // par WorkflowActiveRegistry sur cascade events sink chunk written .
        //
        // Strategy-specific :
        //   DIRECT_COPY : sink emet onSinkChunkWritten avec recordsWritten ;
        //                 le compteur registry.stagingRows reflete les
        //                 rows reellement ecrites en staging DB . On NE
        //                 fallback PAS sur recordsProcessed ( ca refleterait
        //                 transform output , pas sink output -> Phase A bar
        //                 avancerait avant que sink ait ecrit quoi que ce
        //                 soit -> incoherence avec la table workers SINK ) .
        //   MERGE_FILE  : sink filesystem inline , cascade n'emet pas
        //                 d'events sink chunk -> registry.stagingRows reste
        //                 a 0 . On fallback sur snap.recordsProcessed pour
        //                 afficher quand meme une progression .
        long stagingRowsWritten;
        boolean isDirectCopy = strategy != null && "DIRECT_COPY".equals(strategy.sinkStrategy());
        if (isDirectCopy) {
            stagingRowsWritten = registry.stagingRows(correlationId);
        } else {
            stagingRowsWritten = Math.max(
                    registry.stagingRows(correlationId),
                    snap.recordsProcessed());
        }
        long finalRowsWritten = registry.finalRows(correlationId);
        // Phase COMPLETED : on garantit finalRows = expected pour que l'UI
        // bascule a 100 % meme sans hook batch UPSERT granulaire .
        if (FinalizePhaseSnapshotConst.PHASE_COMPLETED.equals(phaseName)
                && finalRowsWritten == 0 && expectedTotal > 0) {
            finalRowsWritten = expectedTotal;
        }

        // Determinate flags : phase A toujours determinate ( cascade event
        // sink chunk emet recordsWritten en temps reel ) . Phase B
        // determinate uniquement pour SHARED_UNLOGGED ou strategies futures
        // exposant des hooks batch granulaires ; sinon le commit final est
        // atomique cote DB et le UI doit afficher une barre indeterminee .
        boolean stagingDeterminate = true;
        // SHARED_UNLOGGED + PER_WORKFLOW_TABLE : table staging visible
        // cross-conn -> count finale observable progressivement ( si lib
        // cascade emit batch progress ) . PER_CONN_TEMP / MERGE_FILE : tx
        // atomique , progres invisible -> indeterminate .
        boolean finalDeterminate = strategy != null
                && ("SHARED_UNLOGGED".equals(strategy.stagingStrategy())
                  || "PER_WORKFLOW_TABLE".equals(strategy.stagingStrategy()));

        return Optional.of(new FinalizeProgressDTO(
                phaseName,
                strategy != null ? strategy.sinkStrategy() : null,
                strategy != null ? strategy.stagingStrategy() : null,
                cascadeStart, cascadeEnd,
                finalizeStart, finalizeEnd,
                rollbackStart, rollbackEnd,
                cascadeDur, finalizeDur, rollbackDur,
                expectedTotal, finalCount, stagingRemaining,
                cascadeTput, finalizeTput,
                phase != null ? phase.errorMessage() : null,
                stagingRowsWritten, finalRowsWritten,
                stagingDeterminate, finalDeterminate));
    }

    private static final class FinalizePhaseSnapshotConst {
        static final String PHASE_COMPLETED =
                fr.inra.oresing.workflow.cascade.history.FinalizePhaseSnapshot.PHASE_COMPLETED;
    }

    // ---------------------------------------------------------------- //
    //  row mappers                                                     //
    // ---------------------------------------------------------------- //

    private DashboardWorkflowDTO mapSummary(ResultSet rs, int rn) throws SQLException {
        return new DashboardWorkflowDTO(
                rs.getObject("correlation_id", UUID.class),
                rs.getString("workflow_type"),
                rs.getObject("user_id", UUID.class),
                rs.getString("user_login"),
                rs.getString("application_name"),
                rs.getString("data_type"),
                rs.getString("resource_name"),
                toInstant(rs.getTimestamp("start_time")),
                toInstant(rs.getTimestamp("end_time")),
                (Long) rs.getObject("duration_ms"),
                rs.getString("status"),
                rs.getLong("records_processed"),
                rs.getLong("records_failed"),
                rs.getInt("chunks_processed"),
                (Double) rs.getObject("progress_percentage"),
                rs.getLong("bytes_total"),
                0L,
                List.of(),
                List.of(),
                null,
                null,
                List.of());
    }

    @SuppressWarnings("unchecked")
    private DashboardWorkflowDTO.Detail mapDetail(ResultSet rs, int rn) throws SQLException {
        DashboardWorkflowDTO summary = mapSummary(rs, rn);
        String errorsJson = rs.getString("errors_json");
        String metadataJson = rs.getString("metadata_json");

        List<String> errors = null;
        Map<String, Object> metadata = null;
        try {
            if (errorsJson != null && !errorsJson.isBlank()) {
                errors = objectMapper.readValue(errorsJson, List.class);
            }
            if (metadataJson != null && !metadataJson.isBlank()) {
                metadata = objectMapper.readValue(metadataJson, Map.class);
            }
        } catch (JsonProcessingException ex) {
            log.warn("Could not parse errors / metadata JSON for workflow_log row : {}", ex.getMessage());
        }
        return new DashboardWorkflowDTO.Detail(
                summary, rs.getString("fatal_error"), errors, metadata);
    }

    private static java.time.Instant toInstant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------- //
    //  config ( admin only )                                           //
    // ---------------------------------------------------------------- //

    public DashboardConfigDTO getConfig() {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        if (!me.isOpenAdomAdmin()) {
            throw new AccessDeniedException("Reserved to openAdomAdmin users");
        }

        DashboardConfigDTO.ImportConfig importCfg = new DashboardConfigDTO.ImportConfig(
                importProperties.getChunkSizeLines(),
                importProperties.getProgressBatchSize(),
                importProperties.getMaxErrorsThreshold(),
                importProperties.getChunksTempDir(),
                importProperties.getProcessedTempDir());

        DashboardConfigDTO.RateLimitConfig rateLimitCfg = new DashboardConfigDTO.RateLimitConfig(
                importRateLimiter.getMaxConcurrentPerUser(),
                maxConcurrentExtractionsPerUser,
                extractionAcquireTimeoutSeconds,
                importRateLimiter.snapshotUsedSlots());

        DashboardConfigDTO.RuntimeInfo runtime = new DashboardConfigDTO.RuntimeInfo(
                resolveCascadeVersion(),
                Runtime.version().feature() + "." + Runtime.version().interim(),
                WorkflowPoolRegistry.useVirtualThreads(),
                registry.size(),
                Map.of());

        // Snapshots live des pools cascade. Si le singleton n'existe pas
        // encore ( aucun workflow n'a tourne ) on retourne une liste vide
        // au lieu de forcer la creation : evite les surprises de
        // configuration ( la creation lit les system properties de cascade ).
        java.util.List<DashboardConfigDTO.CascadePool> pools = WorkflowPoolRegistry.isInitialized()
                ? WorkflowPoolRegistry.getInstance().snapshotPools().stream()
                        .map(p -> new DashboardConfigDTO.CascadePool(
                                p.stage(), p.threadNamePrefix(),
                                p.configuredThreads(), p.activeCount(), p.poolSize(),
                                p.queueSize(), p.queueCapacity(),
                                p.taskCount(), p.completedTaskCount(),
                                p.virtualThreads()))
                        .toList()
                : java.util.List.of();

        // Defauts cascade ( WorkflowConfig.defaults() ).
        fr.inrae.ore.cascade.model.workflow.WorkflowConfig wfDefaults =
                fr.inrae.ore.cascade.model.workflow.WorkflowConfig.defaults();
        fr.inrae.ore.cascade.model.ratelimit.RateLimitConfig rlDefaults = wfDefaults.rateLimit();
        DashboardConfigDTO.CascadeDefaults cascadeDefaults = new DashboardConfigDTO.CascadeDefaults(
                wfDefaults.sourceChunkSize(),
                wfDefaults.collectorChunkSize(),
                wfDefaults.maxErrors(),
                wfDefaults.enableMetrics(),
                wfDefaults.fallbackParallelism(),
                wfDefaults.sourceParallelism(),
                wfDefaults.transformParallelism(),
                wfDefaults.sinkParallelism(),
                wfDefaults.sourceQueueSize(),
                wfDefaults.transformQueueSize(),
                wfDefaults.sinkQueueSize(),
                rlDefaults.maxWorkflowsPerUser(),
                rlDefaults.acquireTimeoutSeconds(),
                rlDefaults.rejectionPolicy() != null ? rlDefaults.rejectionPolicy().name() : null,
                rlDefaults.enabled());

        return new DashboardConfigDTO(importCfg, rateLimitCfg, runtime, pools, cascadeDefaults);
    }

    // ---------------------------------------------------------------- //
    //  cancel ( admin OR owner )                                       //
    // ---------------------------------------------------------------- //

    /**
     * Result of a cancel attempt , surfaced as the HTTP response body.
     *
     * @param signalled true if the cancel signal was registered for the
     *                  first time ( cascade has updated its internal
     *                  ChunkCancellationRegistry ; chunks already running
     *                  finish , pending chunks throw at their next
     *                  cancellation check )
     */
    public record CancelResult(boolean signalled) { }

    /**
     * Cancels an in-progress workflow.
     *
     * <p>Authorisation : the caller must be either openAdomAdmin , or the
     * owner of the workflow ( same userId on the snapshot ). Otherwise we
     * deliberately return 404 to prevent id enumeration ( instead of 403
     * which would confirm the workflow exists ).
     *
     * <p>Async : the call returns as soon as the cancellation flag is
     * registered. The actual finalisation in CANCELLED status happens
     * later , when chunks observe the flag.
     *
     * @return result with {@code signalled = true} if the cancellation
     *         flag was set for the first time , {@code false} if the
     *         workflow was already cancelling
     * @throws java.util.NoSuchElementException if no active workflow
     *         matches {@code correlationId} or it is not visible to the
     *         caller
     */
    public CancelResult cancelWorkflow(UUID correlationId) {
        CurrentUserRoles me = authenticationService.getCurrentUserRoles();
        WorkflowSnapshot snap = registry.find(correlationId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Workflow not found : " + correlationId));
        boolean owner = me.userId() != null && me.userId().equals(snap.userId());
        if (!me.isOpenAdomAdmin() && !owner) {
            // Same response shape as "not found" to avoid leaking which
            // workflows exist to non-owner non-admin users.
            throw new java.util.NoSuchElementException(
                    "Workflow not found : " + correlationId);
        }
        boolean signalled = WorkflowEventBus.getInstance()
                .cancel(correlationId.toString(),
                        "Cancelled by " + (me.userLogin() != null ? me.userLogin() : me.userId()));
        return new CancelResult(signalled);
    }
}
