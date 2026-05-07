package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.ImportRateLimiter;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour DashboardService (sans contexte Spring / sans Docker).
 * Les dépendances sont toutes mockées via Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService - tests unitaires")
@Tag("domain.model")
class DashboardServiceTest {

    @Mock
    private WorkflowActiveRegistry registry;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private ImportProperties importProperties;
    @Mock
    private ImportRateLimiter importRateLimiter;

    @InjectMocks
    private DashboardService service;

    // ---- helpers ---

    private static CurrentUserRoles adminRoles() {
        // "openAdomAdmin" est le nom SQL du rôle admin
        return new CurrentUserRoles(List.of("openAdomAdmin"), false, null);
    }

    private static CurrentUserRoles regularRoles(UUID userId, String login) {
        OreSiUser user = new OreSiUser();
        user.setLogin(login);
        // OreSiEntity a @Getter/@Setter (Lombok) — setId(UUID) disponible
        user.setId(userId);
        return new CurrentUserRoles(List.of("someOtherRole"), false, user);
    }

    private static WorkflowSnapshot buildSnapshot(UUID correlationId, UUID userId) {
        return WorkflowSnapshot.minimal(
                correlationId,
                "IMPORT",
                userId,
                "testUser",
                "myApp",
                "myDataType",
                "myResource.csv",
                Instant.now(),
                "RUNNING",
                100L, 0L, 5, 50.0, 2048L,
                200L,
                List.of(),
                List.of()
        );
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

            List<DashboardWorkflowDTO> result = service.listInProgress();

            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    //  findDetail
    // =========================================================================
    @Nested
    @DisplayName("findDetail(UUID)")
    class FindDetailTest {

        @Test
        @DisplayName("Admin trouve un workflow en cours")
        void adminFindsLiveWorkflow() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

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

            Optional<DashboardWorkflowDTO.Detail> result = service.findDetail(corrId);

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Utilisateur non-propriétaire ne voit pas le workflow (Optional.empty)")
        void nonOwnerDoesNotSeeLiveWorkflow() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(otherUserId, "charlie"));
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            Optional<DashboardWorkflowDTO.Detail> result = service.findDetail(corrId);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Retourne Optional.empty quand le workflow n'existe pas")
        void returnsEmptyWhenNotFound() {
            UUID corrId = UUID.randomUUID();

            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());
            when(jdbc.query(any(String.class),
                    any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                    any(org.springframework.jdbc.core.RowMapper.class)))
                    .thenReturn(List.of());

            Optional<DashboardWorkflowDTO.Detail> result = service.findDetail(corrId);

            assertTrue(result.isEmpty());
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
            when(importRateLimiter.snapshotUsedSlots()).thenReturn(java.util.Map.of());
            when(registry.size()).thenReturn(0);

            DashboardConfigDTO config = assertDoesNotThrow(() -> service.getConfig());
            assertNotNull(config);
            assertNotNull(config.importConfig());
            assertNotNull(config.rateLimit());
            assertNotNull(config.runtime());
        }

        @Test
        @DisplayName("Utilisateur non-admin reçoit AccessDeniedException")
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
        @DisplayName("Lance NoSuchElementException si le workflow n'existe pas")
        void throwsWhenWorkflowNotFound() {
            UUID corrId = UUID.randomUUID();
            when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles());
            when(registry.find(corrId)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> service.cancelWorkflow(corrId));
        }

        @Test
        @DisplayName("Utilisateur non-propriétaire non-admin reçoit NoSuchElementException")
        void nonOwnerNonAdminGets404() {
            UUID corrId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            WorkflowSnapshot snap = buildSnapshot(corrId, ownerId);

            when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles(otherId, "eve"));
            when(registry.find(corrId)).thenReturn(Optional.of(snap));

            assertThrows(NoSuchElementException.class, () -> service.cancelWorkflow(corrId));
        }
    }

    // =========================================================================
    //  DashboardService.CancelResult record
    // =========================================================================
    @Nested
    @DisplayName("CancelResult - record")
    class CancelResultTest {

        @Test
        @DisplayName("CancelResult(true) expose signalled = true")
        void cancelResultSignalledTrue() {
            DashboardService.CancelResult result = new DashboardService.CancelResult(true);
            assertTrue(result.signalled());
        }

        @Test
        @DisplayName("CancelResult(false) expose signalled = false")
        void cancelResultSignalledFalse() {
            DashboardService.CancelResult result = new DashboardService.CancelResult(false);
            assertFalse(result.signalled());
        }
    }
}