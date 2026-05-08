package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.monitoring.session.JwtBlacklistRegistry;
import fr.inra.oresing.monitoring.session.SessionInfo;
import fr.inra.oresing.monitoring.session.UserSessionLogRepository;
import fr.inra.oresing.monitoring.session.UserSessionLogWriter;
import fr.inra.oresing.monitoring.session.UserSessionRegistry;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.ImportRateLimiter;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.FinalizePhaseSnapshot;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import fr.inra.oresing.workflow.cascade.pipeline.PipelineRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires DashboardService — sans contexte Spring / sans Docker.
 * Toutes les dépendances sont mockées via Mockito.
 *
 * <p>Couverture ciblée (new-code Sonar) :
 * listHistory, mapSummary, mapDetail, listActiveSessions, disconnectSession,
 * listBlacklist/removeBlacklistEntry/clearBlacklist, listSessionsHistory,
 * pipeline, cascadePools, finalizeAggregate, finalizeProgress.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService - tests unitaires")
@Tag("domain.model")
class DashboardServiceTest {

    // ─── mocks (constructeur @RequiredArgsConstructor) ────────────────────────
    @Mock private WorkflowActiveRegistry registry;
    @Mock private PipelineRegistry pipelineRegistry;
    @Mock private UserSessionRegistry sessionRegistry;
    @Mock private UserSessionLogRepository sessionLogRepository;
    @Mock private UserSessionLogWriter sessionLogWriter;
    @Mock private JwtBlacklistRegistry jwtBlacklist;
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private AuthenticationService authenticationService;
    @Mock private ImportProperties importProperties;
    @Mock private ImportRateLimiter importRateLimiter;

    @InjectMocks
    private DashboardService service;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static CurrentUserRoles adminRoles() {
        return new CurrentUserRoles(List.of("openAdomAdmin"), false, null);
    }

    private static CurrentUserRoles regularRoles(UUID userId, String login) {
        OreSiUser user = new OreSiUser();
        user.setLogin(login);
        user.setId(userId);
        return new CurrentUserRoles(List.of("someOtherRole"), false, user);
    }

    /**
     * applicationName = "myapp" (minuscules) pour que SAFE_IDENT
     * '^[a-z_][a-z0-9_]*$' matche si besoin dans finalizeProgress.
     */
    private static WorkflowSnapshot buildSnapshot(UUID correlationId, UUID userId) {
        return WorkflowSnapshot.minimal(
                correlationId, "IMPORT", userId, "testUser",
                "myapp", "myDataType", "myResource.csv",
                Instant.now(), "RUNNING",
                100L, 0L, 5, 50.0, 2048L, 200L,
                List.of(), List.of());
    }

    private static SessionInfo buildSession(UUID sessionId, UUID userId, String jwtHash) {
        return new SessionInfo(sessionId, userId, "alice", "127.0.0.1", "TestUA/1.0",
                Instant.now(), Instant.now().plusSeconds(300),
                Instant.now(), SessionInfo.END_KICK, jwtHash);
    }

    // =========================================================================
    //  listInProgress
    // =========================================================================
    @Nested
    @DisplayName("listInProgress()")
    class ListInProgressTest {

        @Test
        @DisplayName("Admin voit tous les workflows (filtre = null)")
        void adminSeesAllWorkflows() {
            UUID wId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(wId, UUID.randomUUID());
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.list(null)).thenReturn(List.of(snap));

            List<DashboardWorkflowDTO> result = service.listInProgress();

            assertEquals(1, result.size());
            assertEquals(wId, result.get(0).correlationId());
        }

        @Test
        @DisplayName("Utilisateur régulier ne voit que ses propres workflows")
        void regularUserSeesOnlyOwn() {
            UUID myUserId = UUID.randomUUID();
            UUID wId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(wId, myUserId);

            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(myUserId, "alice"));
            when(registry.list(myUserId)).thenReturn(List.of(snap));

            List<DashboardWorkflowDTO> result = service.listInProgress();

            assertEquals(1, result.size());
            assertEquals(wId, result.get(0).correlationId());
        }

        @Test
        @DisplayName("Retourne liste vide quand aucun workflow")
        void returnsEmptyListWhenNoWorkflows() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.list(null)).thenReturn(List.of());

            assertTrue(service.listInProgress().isEmpty());
        }
    }

    // =========================================================================
    //  listHistory  +  mapSummary / toInstant / clamp
    // =========================================================================
    @Nested
    @DisplayName("listHistory()")
    class ListHistoryTest {

        @Test
        @DisplayName("Admin — liste vide, filtres null")
        void adminListHistoryEmpty() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(0L);
            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO>>any()))
                    .thenReturn(List.of());

            DashboardWorkflowDTO.Page page =
                    service.listHistory(null, null, null, null, null, null);

            assertEquals(0L, page.total());
            assertTrue(page.items().isEmpty());
        }

        @Test
        @DisplayName("Admin — filtres type/status/app/user appliqués")
        void adminListHistoryWithFilters() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(3L);
            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO>>any()))
                    .thenReturn(List.of());

            DashboardWorkflowDTO.Page page =
                    service.listHistory(10, 0, "IMPORT", "FINISHED", "myapp", "alice");

            assertEquals(3L, page.total());
        }

        @Test
        @DisplayName("User régulier — WHERE inclut userId")
        void regularUserListHistory() {
            UUID uid = UUID.randomUUID();
            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(uid, "bob"));
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(1L);
            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO>>any()))
                    .thenReturn(List.of());

            DashboardWorkflowDTO.Page page =
                    service.listHistory(null, null, null, null, null, null);

            assertEquals(1L, page.total());
        }

        @Test
        @DisplayName("limit > MAX_LIMIT → clampé à 500 ; offset négatif → 0")
        void limitClampedAndOffsetNormalized() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(0L);
            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO>>any()))
                    .thenReturn(List.of());

            DashboardWorkflowDTO.Page page =
                    service.listHistory(9999, -5, null, null, null, null);

            assertEquals(500, page.limit());
            assertEquals(0, page.offset());
        }

        @Test
        @DisplayName("count() null → total = 0")
        void nullCountHandled() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(null);
            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO>>any()))
                    .thenReturn(List.of());

            DashboardWorkflowDTO.Page page =
                    service.listHistory(null, null, null, null, null, null);

            assertEquals(0L, page.total());
        }

        /**
         * Couvre mapSummary + toInstant (non-null et null).
         */
        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("mapSummary — mapper invoqué avec un ResultSet mocké")
        void mapSummaryViaRowMapperCaptor() throws Exception {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            Instant now = Instant.now();

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(1L);

            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO>>any()))
                    .thenAnswer(invocation -> {
                        RowMapper<DashboardWorkflowDTO> mapper = invocation.getArgument(2);
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getObject("correlation_id", UUID.class)).thenReturn(corrId);
                        when(rs.getString("workflow_type")).thenReturn("IMPORT");
                        when(rs.getObject("user_id", UUID.class)).thenReturn(ownerId);
                        when(rs.getString("user_login")).thenReturn("alice");
                        when(rs.getString("application_name")).thenReturn("myapp");
                        when(rs.getString("data_type")).thenReturn("referentiel");
                        when(rs.getString("resource_name")).thenReturn("file.csv");
                        when(rs.getTimestamp("start_time")).thenReturn(Timestamp.from(now));
                        when(rs.getTimestamp("end_time")).thenReturn(null); // toInstant(null)
                        when(rs.getObject("duration_ms")).thenReturn(5000L);
                        when(rs.getString("status")).thenReturn("FINISHED");
                        when(rs.getLong("records_processed")).thenReturn(500L);
                        when(rs.getLong("records_failed")).thenReturn(2L);
                        when(rs.getInt("chunks_processed")).thenReturn(3);
                        when(rs.getObject("progress_percentage")).thenReturn(100.0);
                        when(rs.getLong("bytes_total")).thenReturn(4096L);
                        when(rs.getTimestamp("last_heartbeat_at"))
                                .thenReturn(Timestamp.from(now));
                        return List.of(mapper.mapRow(rs, 0));
                    });

            DashboardWorkflowDTO.Page page =
                    service.listHistory(null, null, null, null, null, null);

            assertEquals(1, page.items().size());
            DashboardWorkflowDTO dto = page.items().getFirst();
            assertEquals(corrId, dto.correlationId());
            assertEquals("IMPORT", dto.workflowType());
            assertEquals("FINISHED", dto.status());
            assertEquals(500L, dto.recordsProcessed());
        }
    }

    // =========================================================================
    //  findDetail  +  mapDetail
    // =========================================================================
    @Nested
    @DisplayName("findDetail(UUID)")
    class FindDetailTest {

        @Test
        @DisplayName("Admin trouve un workflow en cours (registry)")
        void adminFindsLiveWorkflow() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            Optional<DashboardWorkflowDTO.Detail> result = service.findDetail(corrId);

            assertTrue(result.isPresent());
            assertEquals(corrId, result.get().summary().correlationId());
        }

        @Test
        @DisplayName("Propriétaire trouve son propre workflow en cours")
        void ownerFindsOwnLiveWorkflow() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(ownerId, "bob"));
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            assertTrue(service.findDetail(corrId).isPresent());
        }

        @Test
        @DisplayName("Utilisateur non-propriétaire → Optional.empty")
        void nonOwnerDoesNotSeeLiveWorkflow() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(otherId, "charlie"));
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            assertTrue(service.findDetail(corrId).isEmpty());
        }

        @Test
        @DisplayName("Workflow absent registry et absent BDD → Optional.empty")
        void returnsEmptyWhenNotFound() {
            UUID corrId = UUID.randomUUID();

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());
            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO.Detail>>any()))
                    .thenReturn(List.of());

            assertTrue(service.findDetail(corrId).isEmpty());
        }

        /**
         * Couvre mapDetail, parsing JSON errors/metadata.
         */
        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("mapDetail — JSON errors/metadata parsés depuis la BDD")
        void mapDetailViaRowMapperCaptor() throws Exception {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            Instant now = Instant.now();

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());

            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO.Detail>>any()))
                    .thenAnswer(invocation -> {
                        RowMapper<DashboardWorkflowDTO.Detail> mapper = invocation.getArgument(2);
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getObject("correlation_id", UUID.class)).thenReturn(corrId);
                        when(rs.getString("workflow_type")).thenReturn("IMPORT");
                        when(rs.getObject("user_id", UUID.class)).thenReturn(ownerId);
                        when(rs.getString("user_login")).thenReturn("alice");
                        when(rs.getString("application_name")).thenReturn("myapp");
                        when(rs.getString("data_type")).thenReturn("referentiel");
                        when(rs.getString("resource_name")).thenReturn("file.csv");
                        when(rs.getTimestamp("start_time")).thenReturn(Timestamp.from(now));
                        when(rs.getTimestamp("end_time"))
                                .thenReturn(Timestamp.from(now.plusSeconds(10)));
                        when(rs.getObject("duration_ms")).thenReturn(10000L);
                        when(rs.getString("status")).thenReturn("FINISHED");
                        when(rs.getLong("records_processed")).thenReturn(200L);
                        when(rs.getLong("records_failed")).thenReturn(1L);
                        when(rs.getInt("chunks_processed")).thenReturn(2);
                        when(rs.getObject("progress_percentage")).thenReturn(100.0);
                        when(rs.getLong("bytes_total")).thenReturn(1024L);
                        when(rs.getTimestamp("last_heartbeat_at")).thenReturn(null);
                        when(rs.getString("errors_json")).thenReturn("[\"err1\",\"err2\"]");
                        when(rs.getString("fatal_error")).thenReturn("FATAL_MSG");
                        when(rs.getString("metadata_json")).thenReturn("{\"key\":\"val\"}");
                        return List.of(mapper.mapRow(rs, 0));
                    });

            Optional<DashboardWorkflowDTO.Detail> result = service.findDetail(corrId);

            assertTrue(result.isPresent());
            assertEquals(corrId, result.get().summary().correlationId());
            assertNotNull(result.get().errors());
            assertEquals(2, result.get().errors().size());
            assertEquals("FATAL_MSG", result.get().fatalError());
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("mapDetail JSON invalide — ignoré silencieusement")
        void mapDetailInvalidJsonIgnored() throws Exception {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            Instant now = Instant.now();

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());

            when(jdbc.query(anyString(), any(SqlParameterSource.class),
                    ArgumentMatchers.<RowMapper<DashboardWorkflowDTO.Detail>>any()))
                    .thenAnswer(invocation -> {
                        RowMapper<DashboardWorkflowDTO.Detail> mapper = invocation.getArgument(2);
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getObject("correlation_id", UUID.class)).thenReturn(corrId);
                        when(rs.getString("workflow_type")).thenReturn("IMPORT");
                        when(rs.getObject("user_id", UUID.class)).thenReturn(ownerId);
                        when(rs.getString("user_login")).thenReturn("alice");
                        when(rs.getString("application_name")).thenReturn("myapp");
                        when(rs.getString("data_type")).thenReturn("ref");
                        when(rs.getString("resource_name")).thenReturn("f.csv");
                        when(rs.getTimestamp("start_time")).thenReturn(Timestamp.from(now));
                        when(rs.getTimestamp("end_time")).thenReturn(null);
                        when(rs.getObject("duration_ms")).thenReturn(null);
                        when(rs.getString("status")).thenReturn("FINISHED");
                        when(rs.getLong("records_processed")).thenReturn(10L);
                        when(rs.getLong("records_failed")).thenReturn(0L);
                        when(rs.getInt("chunks_processed")).thenReturn(1);
                        when(rs.getObject("progress_percentage")).thenReturn(null);
                        when(rs.getLong("bytes_total")).thenReturn(512L);
                        when(rs.getTimestamp("last_heartbeat_at")).thenReturn(null);
                        when(rs.getString("errors_json")).thenReturn("INVALID_JSON{{{");
                        when(rs.getString("fatal_error")).thenReturn(null);
                        when(rs.getString("metadata_json")).thenReturn("NOT_JSON");
                        return List.of(mapper.mapRow(rs, 0));
                    });

            // Ne doit pas lever d'exception
            assertDoesNotThrow(() -> service.findDetail(corrId));
        }
    }

    // =========================================================================
    //  sessions admin (requireAdmin, listActiveSessions, disconnectSession,
    //                  blacklist CRUD, listSessionsHistory)
    // =========================================================================
    @Nested
    @DisplayName("Sessions admin")
    class SessionsAdminTest {

        @Test
        @DisplayName("listActiveSessions — admin → liste vide")
        void listActiveSessionsAdminEmpty() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionRegistry.listActive(any(Instant.class))).thenReturn(List.of());

            assertTrue(service.listActiveSessions().isEmpty());
        }

        @Test
        @DisplayName("listActiveSessions — admin → une session active")
        void listActiveSessionsAdminOneSession() {
            UUID sid = UUID.randomUUID();
            SessionInfo session = buildSession(sid, UUID.randomUUID(), "h1");

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionRegistry.listActive(any(Instant.class))).thenReturn(List.of(session));

            assertEquals(1, service.listActiveSessions().size());
        }

        @Test
        @DisplayName("listActiveSessions — non-admin → AccessDeniedException (couvre requireAdmin)")
        void listActiveSessionsNonAdminDenied() {
            when(authenticationService.getCurrentUserRoles())
                    .thenReturn(regularRoles(UUID.randomUUID(), "eve"));

            assertThrows(AccessDeniedException.class, () -> service.listActiveSessions());
        }

        @Test
        @DisplayName("disconnectSession — session avec jwtHash → blacklist + logAsync")
        void disconnectSessionWithJwtHash() {
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            SessionInfo finished = buildSession(sessionId, userId, "abc123hash");

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionRegistry.finish(eq(sessionId), eq(SessionInfo.END_KICK), any(Instant.class)))
                    .thenReturn(Optional.of(finished));

            assertDoesNotThrow(() -> service.disconnectSession(sessionId));

            verify(jwtBlacklist).add(eq("abc123hash"), eq(userId), eq("alice"),
                    eq(sessionId), any(Instant.class), any(Instant.class));
            verify(sessionLogWriter).logAsync(any());
        }

        @Test
        @DisplayName("disconnectSession — sans jwtHash → pas de blacklist (log warn)")
        void disconnectSessionWithoutJwtHash() {
            UUID sessionId = UUID.randomUUID();
            SessionInfo finished = buildSession(sessionId, UUID.randomUUID(), null);

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionRegistry.finish(eq(sessionId), eq(SessionInfo.END_KICK), any(Instant.class)))
                    .thenReturn(Optional.of(finished));

            assertDoesNotThrow(() -> service.disconnectSession(sessionId));

            verify(jwtBlacklist, never()).add(any(), any(), any(), any(), any(), any());
            verify(sessionLogWriter).logAsync(any());
        }

        @Test
        @DisplayName("disconnectSession — session introuvable → NoSuchElementException")
        void disconnectSessionNotFound() {
            UUID sessionId = UUID.randomUUID();

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionRegistry.finish(eq(sessionId), any(), any()))
                    .thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class,
                    () -> service.disconnectSession(sessionId));
        }

        @Test
        @DisplayName("listBlacklist — admin → liste vide")
        void listBlacklistAdmin() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jwtBlacklist.list()).thenReturn(List.of());

            assertTrue(service.listBlacklist().isEmpty());
        }

        @Test
        @DisplayName("removeBlacklistEntry — entrée présente → true")
        void removeBlacklistEntryFound() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jwtBlacklist.remove("h1")).thenReturn(true);

            assertTrue(service.removeBlacklistEntry("h1"));
        }

        @Test
        @DisplayName("removeBlacklistEntry — absente → false")
        void removeBlacklistEntryAbsent() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jwtBlacklist.remove("h2")).thenReturn(false);

            assertFalse(service.removeBlacklistEntry("h2"));
        }

        @Test
        @DisplayName("clearBlacklist — renvoie le nombre d'entrées supprimées")
        void clearBlacklistAdmin() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(jwtBlacklist.clear()).thenReturn(7);

            assertEquals(7, service.clearBlacklist());
        }

        @Test
        @DisplayName("listSessionsHistory — admin, filtres null → page vide")
        void listSessionsHistoryAdminEmpty() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionLogRepository.findHistory(null, null, null, 100, 0))
                    .thenReturn(List.of());
            when(sessionLogRepository.count(null, null, null)).thenReturn(0L);

            SessionDTO.Page page = service.listSessionsHistory(null, null, null, null);

            assertEquals(0L, page.total());
            assertTrue(page.items().isEmpty());
        }

        @Test
        @DisplayName("listSessionsHistory — avec filtres login / reason")
        void listSessionsHistoryWithFilters() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(sessionLogRepository.findHistory(null, "alice", "KICK", 20, 0))
                    .thenReturn(List.of());
            when(sessionLogRepository.count(null, "alice", "KICK")).thenReturn(5L);

            SessionDTO.Page page = service.listSessionsHistory(20, 0, "alice", "KICK");

            assertEquals(5L, page.total());
        }
    }

    // =========================================================================
    //  pipeline
    // =========================================================================
    @Nested
    @DisplayName("pipeline(UUID)")
    class PipelineTest {

        @Test
        @DisplayName("Workflow introuvable → Optional.empty")
        void pipelineWorkflowNotFound() {
            UUID corrId = UUID.randomUUID();
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());

            assertTrue(service.pipeline(corrId).isEmpty());
        }

        @Test
        @DisplayName("Workflow trouvé, pas de snapshot cascade → Optional.empty")
        void pipelineNoCascadeSnapshot() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(pipelineRegistry.snapshot(corrId)).thenReturn(Optional.empty());

            assertTrue(service.pipeline(corrId).isEmpty());
        }

        @Test
        @DisplayName("Non-propriétaire, non-admin → Optional.empty")
        void pipelineNonOwnerGets404() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

            when(authenticationService.getCurrentUserRoles())
                    .thenReturn(regularRoles(otherId, "eve"));
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            assertTrue(service.pipeline(corrId).isEmpty());
        }
    }

    // =========================================================================
    //  cascadePools  (poolReloader est null car @Autowired(required=false))
    // =========================================================================
    @Nested
    @DisplayName("cascadePools()")
    class CascadePoolsTest {

        @Test
        @DisplayName("poolReloader absent → 4 pools stage=UNKNOWN, parallelism=0")
        void cascadePoolsWithNullPoolReloader() {
            PipelinePoolsDTO pools = assertDoesNotThrow(() -> service.cascadePools());

            assertNotNull(pools);
            // PoolDTO.from(null) = PoolDTO("UNKNOWN", 0, 0, 0, 0)
            assertEquals("UNKNOWN", pools.source().stage());
            assertEquals("UNKNOWN", pools.transform().stage());
            assertEquals("UNKNOWN", pools.sink().stage());
            assertEquals("UNKNOWN", pools.ordering().stage());
        }
    }

    // =========================================================================
    //  finalizeAggregate
    // =========================================================================
    @Nested
    @DisplayName("finalizeAggregate()")
    class FinalizeAggregateTest {

        @Test
        @DisplayName("Admin, aucun workflow actif → tout à zéro")
        void noActiveWorkflows() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.list(null)).thenReturn(List.of());

            FinalizeAggregateDTO agg = service.finalizeAggregate();

            assertEquals(0, agg.nbActive());
            assertEquals(0, agg.nbFinalize());
            assertEquals(0, agg.nbRollback());
            assertEquals(0, agg.nbCompleted());
            assertEquals(0L, agg.expectedTotalSum());
        }

        @Test
        @DisplayName("Admin, 1 workflow CASCADE_RUNNING → nbActive=1, nbFinalize=0")
        void oneWorkflowCascadeRunning() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.list(null)).thenReturn(List.of(snap));
            // Pour finalizeProgress() appelé en interne :
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.empty());
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.empty());
            when(registry.finalRows(corrId)).thenReturn(50L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());

            FinalizeAggregateDTO agg = service.finalizeAggregate();

            assertEquals(1, agg.nbActive());
            assertEquals(0, agg.nbFinalize());
        }

        @Test
        @DisplayName("User régulier filtre sur son userId")
        void regularUserAggregateFiltered() {
            UUID uid = UUID.randomUUID();
            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(uid, "alice"));
            when(registry.list(uid)).thenReturn(List.of());

            assertEquals(0, service.finalizeAggregate().nbActive());
        }
    }

    // =========================================================================
    //  finalizeProgress
    // =========================================================================
    @Nested
    @DisplayName("finalizeProgress(UUID)")
    class FinalizeProgressTest {

        @Test
        @DisplayName("Workflow introuvable → Optional.empty")
        void workflowNotFound() {
            UUID corrId = UUID.randomUUID();
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());

            assertTrue(service.finalizeProgress(corrId).isEmpty());
        }

        @Test
        @DisplayName("Non-propriétaire → Optional.empty")
        void nonOwnerGetsEmpty() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();

            when(authenticationService.getCurrentUserRoles())
                    .thenReturn(regularRoles(otherId, "dave"));
            when(registry.find(corrId))
                    .thenReturn(Optional.of(buildSnapshot(corrId, ownerId)));

            assertTrue(service.finalizeProgress(corrId).isEmpty());
        }

        @Test
        @DisplayName("Phase null, registryFinal>0, strategy null → FinalizeProgressDTO valide")
        void phaseNullRegistryFinalPositive() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.empty());
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.empty());
            when(registry.finalRows(corrId)).thenReturn(150L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());

            Optional<FinalizeProgressDTO> result = service.finalizeProgress(corrId);

            assertTrue(result.isPresent());
            FinalizeProgressDTO dto = result.get();
            assertEquals("CASCADE_RUNNING", dto.phase());
            assertEquals(150L, dto.finalCount());
            assertEquals(-1L, dto.stagingRemaining()); // strategy null
            assertFalse(dto.finalDeterminate());       // strategy null
        }

        @Test
        @DisplayName("registryFinal=0, JDBC final_count null → finalCount=-1")
        void phaseNullRegistryZeroJdbcNull() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.empty());
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.empty());
            when(registry.finalRows(corrId)).thenReturn(0L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(null);

            Optional<FinalizeProgressDTO> res = service.finalizeProgress(corrId);

            assertTrue(res.isPresent());
            assertEquals(-1L, res.get().finalCount());
        }

        @Test
        @DisplayName("registryFinal=0, JDBC final_count retourne 99 → finalCount=99")
        void phaseNullRegistryZeroJdbcValue() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.empty());
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.empty());
            when(registry.finalRows(corrId)).thenReturn(0L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(99L);

            assertEquals(99L, service.finalizeProgress(corrId).get().finalCount());
        }

        @Test
        @DisplayName("COUNT(*) fallback déclenché quand binaryFileId présent et appName=SAFE_IDENT")
        void jdbcCountFallbackForBinaryFile() {
            UUID corrId = UUID.randomUUID();
            UUID binId = UUID.randomUUID();
            // buildSnapshot utilise appName="myapp" → SAFE_IDENT matche
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.empty());
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.of(binId));
            when(registry.finalRows(corrId)).thenReturn(0L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());
            // 1ère requête = final_count → null ; 2ème = COUNT(*) → 42
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenReturn(null)
                    .thenReturn(42L);

            assertEquals(42L, service.finalizeProgress(corrId).get().finalCount());
        }

        @Test
        @DisplayName("Phase COMPLETED + finalRows=0 → finalRowsWritten=expectedTotal (garantie 100%)")
        void phaseCompletedGuaranteesFullProgress() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            FinalizePhaseSnapshot phase = new FinalizePhaseSnapshot(
                    FinalizePhaseSnapshot.PHASE_COMPLETED,
                    null, null, null, null, null, null, 0L, null);

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.of(phase));
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.empty());
            when(registry.finalRows(corrId)).thenReturn(0L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());

            Optional<FinalizeProgressDTO> result = service.finalizeProgress(corrId);

            assertTrue(result.isPresent());
            assertEquals("COMPLETED", result.get().phase());
            // finalRows garanti = expectedTotal = 200L (recordsTotal du snapshot)
            assertEquals(200L, result.get().finalRowsWritten());
        }

        @Test
        @DisplayName("JDBC RuntimeException → ignorée, finalCount=-1")
        void jdbcExceptionIgnored() {
            UUID corrId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, UUID.randomUUID());

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.of(snap));
            when(registry.findFinalizePhase(corrId)).thenReturn(Optional.empty());
            when(registry.findBinaryFileId(corrId)).thenReturn(Optional.empty());
            when(registry.finalRows(corrId)).thenReturn(0L);
            when(registry.stagingRows(corrId)).thenReturn(0L);
            when(registry.findMergeFilePhase(corrId)).thenReturn(Optional.empty());
            when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                    .thenThrow(new RuntimeException("DB unavailable"));

            assertDoesNotThrow(() -> {
                Optional<FinalizeProgressDTO> res = service.finalizeProgress(corrId);
                assertTrue(res.isPresent());
                assertEquals(-1L, res.get().finalCount());
            });
        }
    }

    // =========================================================================
    //  getConfig
    // =========================================================================
    @Nested
    @DisplayName("getConfig()")
    class GetConfigTest {

        @Test
        @DisplayName("Admin obtient la configuration")
        void adminGetsConfig() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(importProperties.getChunkSizeLines()).thenReturn(1000);
            when(importProperties.getParallelism()).thenReturn(4);
            when(importProperties.getProgressBatchSize()).thenReturn(100);
            when(importProperties.getMaxErrorsThreshold()).thenReturn(50);
            when(importProperties.getChunksTempDir()).thenReturn("/tmp/chunks");
            when(importProperties.getProcessedTempDir()).thenReturn("/tmp/processed");
            when(importRateLimiter.getMaxConcurrentPerUser()).thenReturn(3);
            when(importRateLimiter.snapshotUsedSlots()).thenReturn(Map.of());
            when(registry.size()).thenReturn(0);

            DashboardConfigDTO config = assertDoesNotThrow(() -> service.getConfig());
            assertNotNull(config);
            assertNotNull(config.importConfig());
            assertNotNull(config.rateLimit());
            assertNotNull(config.runtime());
        }

        @Test
        @DisplayName("Utilisateur non-admin → AccessDeniedException")
        void regularUserGetsAccessDenied() {
            when(authenticationService.getCurrentUserRoles())
                    .thenReturn(regularRoles(UUID.randomUUID(), "dave"));

            assertThrows(AccessDeniedException.class, () -> service.getConfig());
        }
    }

    // =========================================================================
    //  cancelWorkflow
    // =========================================================================
    @Nested
    @DisplayName("cancelWorkflow(UUID)")
    class CancelWorkflowTest {

        @Test
        @DisplayName("Workflow introuvable → NoSuchElementException")
        void throwsWhenWorkflowNotFound() {
            UUID corrId = UUID.randomUUID();
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> service.cancelWorkflow(corrId));
        }

        @Test
        @DisplayName("Non-propriétaire, non-admin → NoSuchElementException (pas de 403)")
        void nonOwnerNonAdminGets404() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

            when(authenticationService.getCurrentUserRoles())
                    .thenReturn(regularRoles(otherId, "eve"));
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            assertThrows(NoSuchElementException.class, () -> service.cancelWorkflow(corrId));
        }
    }

    // =========================================================================
    //  CancelResult record
    // =========================================================================
    @Nested
    @DisplayName("CancelResult - record")
    class CancelResultTest {

        @Test
        @DisplayName("CancelResult(true) expose signalled = true")
        void cancelResultSignalledTrue() {
            assertTrue(new DashboardService.CancelResult(true).signalled());
        }

        @Test
        @DisplayName("CancelResult(false) expose signalled = false")
        void cancelResultSignalledFalse() {
            assertFalse(new DashboardService.CancelResult(false).signalled());
        }
    }
}