package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.api.defaults.db.staging.DeferredFinalize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link TxAwareDeferredRunner}.
 *
 * <p>Vérifie les cas nominaux et d'erreur de {@code afterCommit}
 * et {@code afterCompletion} sans Spring context ni base de données.
 */
@Tag("domain.model")
@DisplayName("TxAwareDeferredRunner — bridge DeferredFinalize / TransactionSynchronization")
class TxAwareDeferredRunnerTest {

    private static final UUID CORR_ID = UUID.randomUUID();

    // ─── afterCommit — chemin nominal ────────────────────────────────────────

    @Test
    @DisplayName("afterCommit() : execute(), cleanup() puis onSuccess sont appelés")
    void afterCommitNominal() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        Runnable onSuccess = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, onSuccess, null, null);

        runner.afterCommit();

        verify(deferred).execute();
        verify(deferred).cleanup();
        verify(onSuccess).run();
    }

    @Test
    @DisplayName("afterCommit() : execute() échoue → onPostCommitFailure appelé, onSuccess NON appelé")
    void afterCommitExecuteFails() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        SQLException cause = new SQLException("UPSERT failed");
        doThrow(cause).when(deferred).execute();

        AtomicReference<Throwable> captured = new AtomicReference<>();
        Consumer<Throwable> onFail = captured::set;
        Runnable onSuccess = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, onSuccess, onFail, null);

        runner.afterCommit();

        assertEquals(cause, captured.get());
        verify(onSuccess, never()).run();
        // cleanup ne doit pas être appelé sur le chemin d'erreur
        verify(deferred, never()).cleanup();
    }

    @Test
    @DisplayName("afterCommit() : cleanup() échoue → onSuccess quand même appelé")
    void afterCommitCleanupFails() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        doThrow(new RuntimeException("cleanup failed")).when(deferred).cleanup();
        Runnable onSuccess = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, onSuccess, null, null);

        assertDoesNotThrow(runner::afterCommit);
        verify(onSuccess).run();
    }

    @Test
    @DisplayName("afterCommit() : onSuccess null → pas de NullPointerException")
    void afterCommitNullOnSuccess() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, null, null, null);

        assertDoesNotThrow(runner::afterCommit);
        verify(deferred).execute();
    }

    @Test
    @DisplayName("afterCommit() : onSuccess lève RuntimeException → absorbée silencieusement")
    void afterCommitOnSuccessThrows() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        Runnable onSuccess = () -> { throw new RuntimeException("callback error"); };

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, onSuccess, null, null);

        assertDoesNotThrow(runner::afterCommit);
    }

    // ─── afterCompletion — rollback ──────────────────────────────────────────

    @Test
    @DisplayName("afterCompletion(COMMITTED) : rien ne se passe")
    void afterCompletionCommitted() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        Runnable onRolledBack = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, null, null, onRolledBack);

        runner.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(deferred, never()).cleanup();
        verify(onRolledBack, never()).run();
    }

    @Test
    @DisplayName("afterCompletion(ROLLED_BACK) : cleanup() puis onTxRolledBack appelés")
    void afterCompletionRolledBack() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        Runnable onRolledBack = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, null, null, onRolledBack);

        runner.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(deferred).cleanup();
        verify(onRolledBack).run();
    }

    @Test
    @DisplayName("afterCompletion(UNKNOWN) : cleanup() et onTxRolledBack appelés")
    void afterCompletionUnknown() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        Runnable onRolledBack = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, null, null, onRolledBack);

        runner.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

        verify(deferred).cleanup();
        verify(onRolledBack).run();
    }

    @Test
    @DisplayName("afterCompletion(ROLLED_BACK) : cleanup() échoue → onTxRolledBack quand même appelé")
    void afterCompletionCleanupFails() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);
        doThrow(new RuntimeException("cleanup fail")).when(deferred).cleanup();
        Runnable onRolledBack = mock(Runnable.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, null, null, onRolledBack);

        assertDoesNotThrow(() ->
                runner.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(onRolledBack).run();
    }

    @Test
    @DisplayName("afterCompletion(ROLLED_BACK) : onTxRolledBack null → pas de NullPointerException")
    void afterCompletionNullCallback() throws SQLException {
        DeferredFinalize deferred = mock(DeferredFinalize.class);

        TxAwareDeferredRunner runner = new TxAwareDeferredRunner(
                deferred, CORR_ID, null, null, null);

        assertDoesNotThrow(() ->
                runner.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(deferred).cleanup();
    }
}
