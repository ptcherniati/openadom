package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.workflow.OreSiWorkflowType;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la phase 1 de
 * {@link PublishLifecycleService} . Verifie :
 * <ul>
 *   <li>toggle visuel applique selon l'action ;</li>
 *   <li>reject ( 409 Conflict ) si un workflow lifecycle IN_PROGRESS existe deja sur le fileId ;</li>
 *   <li>publication d'un event {@link PublishLifecycleEvent} avec
 *       champs coherents ;</li>
 *   <li>recordStart workflow_log avec le bon type + status .</li>
 * </ul>
 *
 * <p>Phase 2 ( handler async ) testee separement par integration .
 */
@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class PublishLifecycleServiceTest {

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String APP_NAME  = "demo";
    private static final String DATA_NAME = "site";
    private static final String FILE_NAME = "donnees-2024.csv";

    @Mock private ServiceContainer          serviceContainer;
    @Mock private OreSiRepository           oreSiRepository;
    @Mock private OreSiRepository.RepositoryForApplication repositoryForApplication;
    @Mock private BinaryFileRepository      binaryFileRepository;
    @Mock private WorkflowLogWriter         workflowLogWriter;
    @Mock private WorkflowLogRepository     workflowLogRepository;
    @Mock private ApplicationEventPublisher events;
    @Mock private ApplicationService        applicationService;
    @Mock private AuthenticationService     authenticationService;
    @Mock private Application               application;

    private final PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
    private final ConfigHashService configHashService = new ConfigHashService();
    private PublishLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new PublishLifecycleService(
                serviceContainer, oreSiRepository,
                workflowLogWriter, workflowLogRepository, events, coordinator, configHashService);

        // Lenient : ces stubs sont utilises par la plupart des tests mais
        // pas tous ( ex le test reject_* sort tot apres acquisition advisory lock
        // sans toucher application.getName() ) . Sans lenient , Mockito strict
        // mode leve UnnecessaryStubbingException sur les tests qui short-circuit .
        Mockito.lenient().when(serviceContainer.applicationService()).thenReturn(applicationService);
        Mockito.lenient().when(serviceContainer.authenticationService()).thenReturn(authenticationService);
        Mockito.lenient().when(applicationService.getApplication(APP_NAME)).thenReturn(application);
        Mockito.lenient().when(application.getName()).thenReturn(APP_NAME);
        Mockito.lenient().when(oreSiRepository.getRepository(application)).thenReturn(repositoryForApplication);
        Mockito.lenient().when(repositoryForApplication.binaryFile()).thenReturn(binaryFileRepository);

        OreSiUser user = new OreSiUser();
        user.setLogin("jdoe");
        user.setEmail("jdoe@example.org");
        Mockito.lenient().when(authenticationService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void startPublish_phase1_doesNotToggleFlagButPublishesEvent() {
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            UUID correlationId = service.startPublish(APP_NAME, FILE_ID, Locale.FRENCH);
            assertThat(correlationId).isNotNull();
        }

        // Lite-v2 Sprint A : Phase 1 ne touche PAS le flag .
        verify(binaryFileRepository, never()).togglePublishedFlag(any(), any(Boolean.class), any());

        ArgumentCaptor<WorkflowLogEntry> entryCap = ArgumentCaptor.forClass(WorkflowLogEntry.class);
        verify(workflowLogWriter).recordStart(entryCap.capture());
        WorkflowLogEntry entry = entryCap.getValue();
        assertThat(entry.workflowType()).isEqualTo(OreSiWorkflowType.PUBLISH.name());
        assertThat(entry.status()).isEqualTo(WorkflowLogEntry.STATUS_IN_PROGRESS);
        assertThat(entry.metadata()).containsEntry("fileId", FILE_ID.toString());
        assertThat(entry.metadata()).containsEntry("wasPublished", false);

        ArgumentCaptor<PublishLifecycleEvent> evCap = ArgumentCaptor.forClass(PublishLifecycleEvent.class);
        verify(events).publishEvent(evCap.capture());
        PublishLifecycleEvent ev = evCap.getValue();
        assertThat(ev.action()).isEqualTo(PublishLifecycleAction.PUBLISH);
        assertThat(ev.fileId()).isEqualTo(FILE_ID);
        assertThat(ev.wasPublished()).isFalse();
        assertThat(ev.dataName()).isEqualTo(DATA_NAME);
        assertThat(ev.fileName()).isEqualTo(FILE_NAME);
    }

    @Test
    void startUnpublish_phase1_doesNotToggleFlag() {
        BinaryFile bf = stubBinaryFile(true);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            service.startUnpublish(APP_NAME, FILE_ID, Locale.FRENCH);
        }

        // Lite-v2 Sprint A : Phase 1 ne touche PAS le flag .
        verify(binaryFileRepository, never()).togglePublishedFlag(any(), any(Boolean.class), any());

        ArgumentCaptor<PublishLifecycleEvent> evCap = ArgumentCaptor.forClass(PublishLifecycleEvent.class);
        verify(events).publishEvent(evCap.capture());
        assertThat(evCap.getValue().action()).isEqualTo(PublishLifecycleAction.UNPUBLISH);
        assertThat(evCap.getValue().wasPublished()).isTrue();
    }

    @Test
    void startDeleteFile_whenPublished_doesNotToggleFlagInPhase1() {
        BinaryFile bf = stubBinaryFile(true);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            service.startDeleteFile(APP_NAME, FILE_ID, Locale.FRENCH);
        }

        // Lite-v2 Sprint A : suppression effective binaryfile en Phase 2 ,
        // pas de flag toggle en Phase 1 .
        verify(binaryFileRepository, never()).togglePublishedFlag(any(), any(Boolean.class), any());
    }

    @Test
    void startDeleteFile_whenNotPublished_skipsFlagToggle() {
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            service.startDeleteFile(APP_NAME, FILE_ID, Locale.FRENCH);
        }

        verify(binaryFileRepository, never()).togglePublishedFlag(any(), any(Boolean.class), any());
    }

    @Test
    void reject_throwsWorkflowAlreadyInProgress_whenActiveWorkflowExists() {
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        UUID activeCorrId = UUID.randomUUID();
        var details = new WorkflowLogRepository.ActiveWorkflowDetails(
                activeCorrId, OreSiWorkflowType.UNPUBLISH.name(), "jdoe");
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.of(details));

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> service.startPublish(APP_NAME, FILE_ID, Locale.FRENCH))
                .isInstanceOf(WorkflowAlreadyInProgressException.class)
                .satisfies(ex -> {
                    var w = (WorkflowAlreadyInProgressException) ex;
                    assertThat(w.activeCorrelationId()).isEqualTo(activeCorrId);
                    assertThat(w.activeWorkflowType()).isEqualTo("UNPUBLISH");
                    assertThat(w.activeUserLogin()).isEqualTo("jdoe");
                    assertThat(w.fileId()).isEqualTo(FILE_ID);
                });
        }
        // L'audit reste intact : aucun recordStart / recordEnd / markCancelled
        // n'est emis pour le workflow rejete ( le workflow actif garde son
        // statut IN_PROGRESS , aucune pollution workflow_log ) .
        verify(workflowLogWriter, never()).recordStart(any());
        verify(workflowLogWriter, never()).recordEnd(any());
        verify(workflowLogRepository, never()).markCancelled(any(), any());
        verify(events, never()).publishEvent(any(PublishLifecycleEvent.class));
    }

    @Test
    void reject_noActiveWorkflow_proceedsNormally() {
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            service.startPublish(APP_NAME, FILE_ID, Locale.FRENCH);
        }

        verify(workflowLogWriter).recordStart(any());
        verify(events, times(1)).publishEvent(any(PublishLifecycleEvent.class));
    }

    @Test
    void startPhase1_acquiresAdvisoryLockOnFileId_whenJdbcTemplateInjected() throws Exception {
        // Fix R1 concurrence : verifie que startPhase1 acquiert le
        // pg_advisory_xact_lock per fileId AVANT la verification reject
        // ( ordre crucial sinon 2 entries concurrentes peuvent lire workflow_log
        // avant l'INSERT de l'autre et passer la verification rejet en double ) .
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        org.springframework.jdbc.core.JdbcTemplate jdbc =
                Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        java.lang.reflect.Field f = PublishLifecycleService.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(service, jdbc);

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            service.startPublish(APP_NAME, FILE_ID, Locale.FRENCH);
        }

        org.mockito.InOrder inOrder = Mockito.inOrder(jdbc, workflowLogRepository, workflowLogWriter);
        inOrder.verify(jdbc).queryForObject(
                eq("SELECT pg_advisory_xact_lock(hashtext(?))"),
                eq(Object.class),
                eq(FILE_ID.toString()));
        inOrder.verify(workflowLogRepository).findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList());
        inOrder.verify(workflowLogWriter).recordStart(any());
    }

    @Test
    void startPhase1_advisoryLockFailure_swallowedAsBestEffort() throws Exception {
        // L'echec de l'advisory lock ( PG unavailable , wrong role ) ne doit
        // PAS faire echouer Phase 1 : le UNIQUE partial index workflow_log
        // ( V10 Flyway ) reste la defense-in-depth qui leve un INSERT conflict
        // explicite si 2 INSERT concurrent passent .
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        org.springframework.jdbc.core.JdbcTemplate jdbc =
                Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        Mockito.when(jdbc.queryForObject(any(String.class), eq(Object.class), any(Object[].class)))
                .thenThrow(new RuntimeException("PG down"));
        java.lang.reflect.Field f = PublishLifecycleService.class.getDeclaredField("jdbcTemplate");
        f.setAccessible(true);
        f.set(service, jdbc);

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            UUID cid = service.startPublish(APP_NAME, FILE_ID, Locale.FRENCH);
            assertThat(cid).isNotNull();
        }
        verify(workflowLogWriter).recordStart(any());
    }

    @Test
    void reject_queriesAllThreeActionTypes() {
        BinaryFile bf = stubBinaryFile(false);
        when(binaryFileRepository.tryFindById(FILE_ID)).thenReturn(Optional.of(bf));
        when(workflowLogRepository.findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), anyList()))
                .thenReturn(Optional.empty());

        try (MockedStatic<OreSiApiRequestContext> ctx = Mockito.mockStatic(OreSiApiRequestContext.class)) {
            ctx.when(OreSiApiRequestContext::getRequestUserId).thenReturn(USER_ID);
            service.startPublish(APP_NAME, FILE_ID, Locale.FRENCH);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> typesCap = ArgumentCaptor.forClass(List.class);
        verify(workflowLogRepository).findActiveDetailsByFileId(eq(APP_NAME), eq(FILE_ID), typesCap.capture());
        assertThat(typesCap.getValue()).containsExactlyInAnyOrder(
                OreSiWorkflowType.PUBLISH.name(),
                OreSiWorkflowType.UNPUBLISH.name(),
                OreSiWorkflowType.DELETE_FILE.name());
    }

    // ------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------

    private BinaryFile stubBinaryFile(boolean published) {
        BinaryFile bf = new BinaryFile();
        bf.setId(FILE_ID);
        bf.setName(FILE_NAME);
        BinaryFileDataset dataset = new BinaryFileDataset();
        dataset.setDatatype(DATA_NAME);
        BinaryFileInfos params = BinaryFileInfos.forPublish(published, USER_ID, "2024-05-12", dataset);
        bf.setParams(params);
        return bf;
    }
}
