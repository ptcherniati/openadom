package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires deterministes pour le predicate
 * {@link PublishLifecyclePhase2Handler#shouldPersistRecordEnd} .
 *
 * <p>Le predicate est extrait du bloc {@code finally} de
 * {@code onPublishLifecycleEvent} pour permettre la verification fine
 * de l'invariant cancel-divergence sans avoir a derouler tout le flow
 * Phase 2 . Seul {@link PublishLifecycleCoordinator} est mocke - on
 * verifie a la fois le comportement attendu ET l'absence d'appel
 * inutile au coordinator quand le statut final n'est pas COMPLETED .
 */
@ExtendWith(MockitoExtension.class)
class PublishLifecyclePhase2HandlerTest {

    @Mock
    private PublishLifecycleCoordinator coordinator;

    @Mock
    private ServiceContainer serviceContainer;

    @Mock
    private DataService dataService;

    @InjectMocks
    private PublishLifecyclePhase2Handler handler;

    private final UUID cid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Mockito.lenient() : certains tests n'appellent pas isCancelled
        // ( early-exit sur status != COMPLETED ) ; sans lenient , Mockito
        // strict releverait UnnecessaryStubbingException sur ce stub partage .
        lenient().when(coordinator.isCancelled(cid)).thenReturn(false);
    }

    @Test
    void persists_when_status_completed_and_not_cancelled() {
        when(coordinator.isCancelled(cid)).thenReturn(false);
        assertThat(handler.shouldPersistRecordEnd(cid, WorkflowLogEntry.STATUS_COMPLETED)).isTrue();
        verify(coordinator).isCancelled(cid);
    }

    @Test
    void skips_when_status_completed_and_cancelled() {
        when(coordinator.isCancelled(cid)).thenReturn(true);
        assertThat(handler.shouldPersistRecordEnd(cid, WorkflowLogEntry.STATUS_COMPLETED)).isFalse();
        verify(coordinator).isCancelled(cid);
    }

    @Test
    void persists_when_status_failed_skips_coordinator_check() {
        // FAILED : on persiste toujours , pas besoin de consulter le coordinator
        assertThat(handler.shouldPersistRecordEnd(cid, WorkflowLogEntry.STATUS_FAILED)).isTrue();
        verifyNoInteractions(coordinator);
    }

    @Test
    void persists_when_status_cancelled_skips_coordinator_check() {
        // CANCELLED : finalStatus deja correct , on persiste sans demander
        // au coordinator ( idempotence assuree par la clause WHERE
        // status='IN_PROGRESS' cote SQL record_workflow ) .
        assertThat(handler.shouldPersistRecordEnd(cid, WorkflowLogEntry.STATUS_CANCELLED)).isTrue();
        verifyNoInteractions(coordinator);
    }

    @Test
    void persists_when_status_null_skips_coordinator_check() {
        assertThat(handler.shouldPersistRecordEnd(cid, null)).isTrue();
        verifyNoInteractions(coordinator);
    }

    @Test
    void persists_when_cid_null_skips_coordinator_check() {
        // cid null = workflow sans correlation -> on ne peut pas demander
        // isCancelled , default safe = persist . Defensive coding pour
        // ne pas lever NPE sur un edge case improbable .
        assertThat(handler.shouldPersistRecordEnd(null, WorkflowLogEntry.STATUS_COMPLETED)).isTrue();
        verifyNoInteractions(coordinator);
    }

    @Test
    void persists_when_status_unknown_skips_coordinator_check() {
        // Statut inconnu ( ex futur ajout sans update du predicate ) :
        // safe default = persist . Mieux qu'un silent skip .
        assertThat(handler.shouldPersistRecordEnd(cid, "WEIRD_STATUS")).isTrue();
        verifyNoInteractions(coordinator);
    }

    /**
     * Tests du hook d'invalidation cache filterList post-Phase 2 .
     *
     * <p>Le hook {@code refreshFilterListCacheSilently} couvre les 3
     * actions PUBLISH / UNPUBLISH / DELETE_FILE qui convergent toutes
     * vers le meme bloc {@code finalStatus = COMPLETED} dans
     * {@code handlePhase2} . Sans ce hook , le cache filterList resterait
     * stale jusqu'a la prochaine action mutante ( TTL = 0 = pas
     * d'eviction auto ) .
     *
     * <p>Granularite verifiee : cle cache {@code app::dataType} ,
     * aucune purge globale .
     */
    @Nested
    class RefreshFilterListCacheSilently {

        private Application application(String name) {
            Application app = new Application();
            app.setName(name);
            return app;
        }

        @Test
        void delegates_to_dataService_refreshFilterListCache_with_app_and_dataType() {
            when(serviceContainer.dataService()).thenReturn(dataService);
            Application app = application("acbb");
            handler.refreshFilterListCacheSilently(app, "tdr1a");
            verify(dataService).refreshFilterListCache(app, "tdr1a");
            verifyNoMoreInteractions(dataService);
        }

        @Test
        void skips_call_when_dataName_is_null() {
            // Garde defensif : sans dataType , refreshFilterListCache
            // construirait une cle "app::null" inutile -> short-circuit
            handler.refreshFilterListCacheSilently(application("acbb"), null);
            verifyNoInteractions(serviceContainer);
            verifyNoInteractions(dataService);
        }

        @Test
        void skips_call_when_dataName_is_blank() {
            handler.refreshFilterListCacheSilently(application("acbb"), "   ");
            verifyNoInteractions(serviceContainer);
            verifyNoInteractions(dataService);
        }

        @Test
        void swallows_runtime_exception_from_dataService() {
            // Best-effort : si le refresh echoue ( DataService bean
            // detruit , SQL down , etc. ) , le hook ne doit PAS faire
            // echouer le workflow Phase 2 . L'ancien cache reste lisible .
            when(serviceContainer.dataService()).thenReturn(dataService);
            doThrow(new RuntimeException("DB down"))
                    .when(dataService).refreshFilterListCache(any(), anyString());
            // Ne doit pas propager l'exception
            handler.refreshFilterListCacheSilently(application("acbb"), "tdr1a");
            verify(dataService).refreshFilterListCache(any(), eq("tdr1a"));
        }
    }
}