package fr.inra.oresing.rest.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.ImportRateLimiter;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import fr.inrae.ore.cascade.core.execution.ExecutionResourceManager;
import fr.inrae.ore.cascade.core.monitoring.WorkflowMonitoringService;
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
                "SELECT count(*) FROM oa_metrics.workflow_log " + where, p, Long.class);

        p.addValue("limit", l);
        p.addValue("offset", o);

        List<DashboardWorkflowDTO> items = jdbc.query(
                "SELECT correlation_id, workflow_type, user_id, user_login, " +
                "       application_name, data_type, resource_name, " +
                "       start_time, end_time, duration_ms, status, " +
                "       records_processed, records_failed, chunks_processed, " +
                "       progress_percentage, bytes_total " +
                "  FROM oa_metrics.workflow_log " +
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

        // 2) fallback on oa_metrics.workflow_log ( finished )
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
                "  FROM oa_metrics.workflow_log " +
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
        return pipelineRegistry.snapshot(correlationId).map(PipelineDTO::from);
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
                null);
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
                ExecutionResourceManager.useVirtualThreads(),
                registry.size(),
                Map.of());

        // Snapshots live des pools cascade. Si le singleton n'existe pas
        // encore ( aucun workflow n'a tourne ) on retourne une liste vide
        // au lieu de forcer la creation : evite les surprises de
        // configuration ( la creation lit les system properties de cascade ).
        java.util.List<DashboardConfigDTO.CascadePool> pools = ExecutionResourceManager.isInitialized()
                ? ExecutionResourceManager.getInstance().snapshotPools().stream()
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
                wfDefaults.defaultParallelism(),
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
        boolean signalled = WorkflowMonitoringService.getDefault()
                .cancel(correlationId.toString(),
                        "Cancelled by " + (me.userLogin() != null ? me.userLogin() : me.userId()));
        return new CancelResult(signalled);
    }
}
