package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires de ImportRateLimiter et ImportRateLimitExceededException.
 * Instanciation directe sans Spring — dépendances mockées.
 */
@DisplayName("ImportRateLimiter — quota d'imports concurrent par utilisateur")
@Tag("domain.model")
class ImportRateLimiterTest {

    private ImportRateLimiter rateLimiter;
    private OpenadomMetrics metrics;
    private WorkflowLogWriter logWriter;
    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        metrics = mock(OpenadomMetrics.class);
        logWriter = mock(WorkflowLogWriter.class);
        authService = mock(AuthenticationService.class);
        // max 2 imports/user , global large ( 100 ) pour ne pas interferer avec
        // les tests per-user .
        rateLimiter = new ImportRateLimiter(2, 100, metrics, logWriter, authService);
        // resolveCurrentLogin() appelle authService.getCurrentUserRoles().userLogin()
        // Le try/catch dans la méthode intercepte RuntimeException, on peut lancer
        Mockito.doThrow(new RuntimeException("no user context in test"))
                .when(authService).getCurrentUserRoles();
    }

    // ------------------------------------------------------------------ //
    //  Configuration                                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTest {

        @Test
        @DisplayName("getMaxConcurrentPerUser() retourne la valeur configurée")
        void maxConcurrentMatchesConfig() {
            assertThat(rateLimiter.getMaxConcurrentPerUser()).isEqualTo(2);
        }

        @Test
        @DisplayName("max = 1 est valide")
        void maxOneIsValid() {
            ImportRateLimiter rl = new ImportRateLimiter(1, 100, metrics, logWriter, authService);
            assertThat(rl.getMaxConcurrentPerUser()).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ //
    //  Plafond global ( tous utilisateurs confondus )                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Plafond global")
    class GlobalCapTest {

        @Test
        @DisplayName("le cap global rejette au-dela de N imports tous users confondus")
        void globalCapRejectsAcrossUsers() {
            // per-user large ( 10 ) , global = 2 : le 3e import ( meme user different )
            // doit etre rejete par le plafond global .
            ImportRateLimiter rl = new ImportRateLimiter(10, 2, metrics, logWriter, authService);
            Mockito.doThrow(new RuntimeException("no user context"))
                    .when(authService).getCurrentUserRoles();

            rl.acquireOrThrow("u1");
            rl.acquireOrThrow("u2");
            assertThatThrownBy(() -> rl.acquireOrThrow("u3"))
                    .isInstanceOf(ImportRateLimitExceededException.class);

            // apres release d'un slot global , une nouvelle acquisition repasse .
            rl.release("u1");
            assertDoesNotThrow(() -> rl.acquireOrThrow("u3"));
            rl.release("u2");
            rl.release("u3");
        }

        @Test
        @DisplayName("le slot per-user est relache si le plafond global rejette")
        void perUserSlotReleasedOnGlobalReject() {
            // global=1 : u1 prend le seul slot global . u2 est rejete globalement ;
            // son slot per-user ne doit PAS rester bloque -> apres release de u1 ,
            // u2 peut acquerir .
            ImportRateLimiter rl = new ImportRateLimiter(10, 1, metrics, logWriter, authService);
            Mockito.doThrow(new RuntimeException("no user context"))
                    .when(authService).getCurrentUserRoles();

            rl.acquireOrThrow("u1");
            assertThatThrownBy(() -> rl.acquireOrThrow("u2"))
                    .isInstanceOf(ImportRateLimitExceededException.class);
            // u2 n'apparait pas comme occupant un slot ( per-user relache ) .
            assertThat(rl.snapshotUsedSlots()).doesNotContainKey("u2");
            rl.release("u1");
            assertDoesNotThrow(() -> rl.acquireOrThrow("u2"));
            rl.release("u2");
        }
    }

    // ------------------------------------------------------------------ //
    //  acquireOrThrow — slots disponibles                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("acquireOrThrow — slots disponibles")
    class AcquireAvailableTest {

        @Test
        @DisplayName("acquisition réussit si slot disponible")
        void acquireSucceedsWhenSlotAvailable() {
            assertDoesNotThrow(() -> rateLimiter.acquireOrThrow("user-1"));
            rateLimiter.release("user-1");
        }

        @Test
        @DisplayName("deux acquisitions réussissent pour le même utilisateur (max=2)")
        void twoAcquisitionsForSameUser() {
            assertDoesNotThrow(() -> {
                rateLimiter.acquireOrThrow("user-2");
                rateLimiter.acquireOrThrow("user-2");
            });
            rateLimiter.release("user-2");
            rateLimiter.release("user-2");
        }

        @Test
        @DisplayName("acquisitions parallèles pour utilisateurs différents sont indépendantes")
        void separateUsersHaveIndependentSlots() {
            assertDoesNotThrow(() -> {
                rateLimiter.acquireOrThrow("userA");
                rateLimiter.acquireOrThrow("userA");
                rateLimiter.acquireOrThrow("userB");
                rateLimiter.acquireOrThrow("userB");
            });
            rateLimiter.release("userA");
            rateLimiter.release("userA");
            rateLimiter.release("userB");
            rateLimiter.release("userB");
        }
    }

    // ------------------------------------------------------------------ //
    //  acquireOrThrow — quota dépassé                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("acquireOrThrow — quota dépassé")
    class AcquireExceededTest {

        @Test
        @DisplayName("lève ImportRateLimitExceededException quand quota atteint")
        void throwsWhenQuotaExceeded() {
            rateLimiter.acquireOrThrow("user-x");
            rateLimiter.acquireOrThrow("user-x");

            assertThatThrownBy(() -> rateLimiter.acquireOrThrow("user-x"))
                    .isInstanceOf(ImportRateLimitExceededException.class);

            rateLimiter.release("user-x");
            rateLimiter.release("user-x");
        }

        @Test
        @DisplayName("le message d'exception contient l'userId et les compteurs")
        void exceptionMessageContainsContext() {
            rateLimiter.acquireOrThrow("victim");
            rateLimiter.acquireOrThrow("victim");

            assertThatThrownBy(() -> rateLimiter.acquireOrThrow("victim"))
                    .isInstanceOf(ImportRateLimitExceededException.class)
                    .hasMessageContaining("victim");

            rateLimiter.release("victim");
            rateLimiter.release("victim");
        }

        @Test
        @DisplayName("recordImportRateLimited() est appelé lors d'une rejection")
        void metricsRecordedOnRejection() {
            rateLimiter.acquireOrThrow("uid-1");
            rateLimiter.acquireOrThrow("uid-1");

            try {
                rateLimiter.acquireOrThrow("uid-1");
            } catch (ImportRateLimitExceededException ignored) { /* expected */ }

            Mockito.verify(metrics, Mockito.atLeastOnce()).recordImportRateLimited();
            rateLimiter.release("uid-1");
            rateLimiter.release("uid-1");
        }
    }

    // ------------------------------------------------------------------ //
    //  release                                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("release — libération des slots")
    class ReleaseTest {

        @Test
        @DisplayName("release() permet une acquisition supplémentaire après quota atteint")
        void releaseAllowsReacquisition() {
            rateLimiter.acquireOrThrow("user-r");
            rateLimiter.acquireOrThrow("user-r");
            rateLimiter.release("user-r");
            assertDoesNotThrow(() -> rateLimiter.acquireOrThrow("user-r"));
            rateLimiter.release("user-r");
            rateLimiter.release("user-r");
        }

        @Test
        @DisplayName("release() d'un userId inconnu est sans effet")
        void releaseUnknownUserIsNoOp() {
            assertDoesNotThrow(() -> rateLimiter.release("unknown-user"));
        }
    }

    // ------------------------------------------------------------------ //
    //  snapshotUsedSlots                                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("snapshotUsedSlots — suivi des slots actifs")
    class SnapshotTest {

        @Test
        @DisplayName("snapshot vide si aucun slot acquis")
        void emptySnapshotInitially() {
            assertThat(rateLimiter.snapshotUsedSlots()).isEmpty();
        }

        @Test
        @DisplayName("snapshot reflète les slots acquisés")
        void snapshotShowsUsedSlots() {
            rateLimiter.acquireOrThrow("snap-user");

            Map<String, Integer> snapshot = rateLimiter.snapshotUsedSlots();
            assertThat(snapshot).containsKey("snap-user");
            assertThat(snapshot.get("snap-user")).isEqualTo(1);

            rateLimiter.release("snap-user");
        }

        @Test
        @DisplayName("snapshot ne contient pas d'utilisateur avec 0 slot utilisé")
        void snapshotExcludesZeroSlotUsers() {
            rateLimiter.acquireOrThrow("released-user");
            rateLimiter.release("released-user");

            // Après release complète, l'utilisateur ne doit pas apparaître
            Map<String, Integer> snapshot = rateLimiter.snapshotUsedSlots();
            assertThat(snapshot).doesNotContainKey("released-user");
        }

        @Test
        @DisplayName("snapshot compte plusieurs slots pour un même utilisateur")
        void snapshotCountsMultipleSlots() {
            rateLimiter.acquireOrThrow("multi-user");
            rateLimiter.acquireOrThrow("multi-user");

            Map<String, Integer> snapshot = rateLimiter.snapshotUsedSlots();
            assertThat(snapshot.get("multi-user")).isEqualTo(2);

            rateLimiter.release("multi-user");
            rateLimiter.release("multi-user");
        }
    }

    // ------------------------------------------------------------------ //
    //  ImportRateLimitExceededException                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ImportRateLimitExceededException")
    class ImportRateLimitExceededExceptionTest {

        @Test
        @DisplayName("getMessage() contient userId, active et max")
        void messageContainsDetails() {
            ImportRateLimitExceededException ex =
                    new ImportRateLimitExceededException("user-42", 3, 3);
            assertThat(ex.getMessage())
                    .contains("user-42")
                    .contains("3");
        }

        @Test
        @DisplayName("est une RuntimeException")
        void isRuntimeException() {
            assertThat(new ImportRateLimitExceededException("u", 1, 1))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}