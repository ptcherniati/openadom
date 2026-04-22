package fr.inra.oresing.rest.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    private final NamedParameterJdbcTemplate jdbc;
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                rs.getLong("bytes_total"));
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
}
