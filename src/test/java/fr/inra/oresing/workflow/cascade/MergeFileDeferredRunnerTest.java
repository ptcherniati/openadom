package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link MergeFileDeferredRunner}.
 */
@Tag("domain.model")
@DisplayName("MergeFileDeferredRunner — MERGE_FILE bridge TX / storeAll")
class MergeFileDeferredRunnerTest {

    @TempDir
    Path tmpDir;

    private static final UUID CORR_ID = UUID.randomUUID();

    private MergeFileDeferredRunner runner(DataRepository repo,
                                           WorkflowTempCleanup cleanup,
                                           StoreAllPathSink sink,
                                           Path mergedPath,
                                           Path processedDir,
                                           Runnable onSuccess,
                                           Consumer<Throwable> onFail,
                                           Runnable onRolledBack) {
        return new MergeFileDeferredRunner(
                repo, cleanup, sink,
                new MergeFileDeferredRunner.WorkflowContext(mergedPath, processedDir, CORR_ID, null),
                new MergeFileDeferredRunner.Callbacks(onSuccess, onFail, onRolledBack));
    }

    // ─── afterCommit — chemin nominal ────────────────────────────────────────

    @Test
    @DisplayName("afterCommit() : storeAll(), cleanup(), onSuccess appelés")
    void afterCommitNominal() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        Runnable          onSuccess = mock(Runnable.class);

        when(repo.storeAll(any(), any(), any())).thenReturn(100L);

        runner(repo, cleanup, sink, merged, processed, onSuccess, null, null)
                .afterCommit();

        verify(repo).storeAll(eq(merged), any(), any());
        verify(sink).recordDeferredRowsWritten(100L);
        verify(cleanup).cleanup(processed);
        verify(onSuccess).run();
    }

    @Test
    @DisplayName("afterCommit() : storeAll() échoue → onPostCommitFailure appelé, onSuccess NON appelé")
    void afterCommitStoreAllFails() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged2.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed2"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        Runnable          onSuccess = mock(Runnable.class);

        RuntimeException cause = new RuntimeException("storeAll failed");
        when(repo.storeAll(any(), any(), any())).thenThrow(cause);

        AtomicReference<Throwable> captured = new AtomicReference<>();

        runner(repo, cleanup, sink, merged, processed, onSuccess, captured::set, null)
                .afterCommit();

        assertEquals(cause, captured.get());
        verify(onSuccess, never()).run();
        // cleanup ne doit PAS être appelé sur le chemin d'erreur
        verify(cleanup, never()).cleanup(any());
    }

    @Test
    @DisplayName("afterCommit() : onSuccess null → pas de NullPointerException")
    void afterCommitNullCallback() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged3.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed3"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        when(repo.storeAll(any(), any(), any())).thenReturn(0L);

        assertDoesNotThrow(() ->
                runner(repo, cleanup, sink, merged, processed, null, null, null)
                        .afterCommit());
    }

    // ─── afterCompletion — rollback ──────────────────────────────────────────

    @Test
    @DisplayName("afterCompletion(COMMITTED) : rien ne se passe")
    void afterCompletionCommitted() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged4.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed4"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        Runnable          onRolledBack = mock(Runnable.class);

        runner(repo, cleanup, sink, merged, processed, null, null, onRolledBack)
                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(repo, never()).storeAll(any(), any(), any());
        verify(onRolledBack, never()).run();
    }

    @Test
    @DisplayName("afterCompletion(ROLLED_BACK) : cleanup() et onTxRolledBack appelés, storeAll ignoré")
    void afterCompletionRolledBack() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged5.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed5"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        Runnable          onRolledBack = mock(Runnable.class);

        runner(repo, cleanup, sink, merged, processed, null, null, onRolledBack)
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(repo, never()).storeAll(any(), any(), any());
        verify(cleanup).cleanup(processed);
        verify(onRolledBack).run();
    }

    @Test
    @DisplayName("afterCompletion(UNKNOWN) : cleanup() et onTxRolledBack appelés")
    void afterCompletionUnknown() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged6.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed6"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        Runnable          onRolledBack = mock(Runnable.class);

        runner(repo, cleanup, sink, merged, processed, null, null, onRolledBack)
                .afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

        verify(cleanup).cleanup(processed);
        verify(onRolledBack).run();
    }

    @Test
    @DisplayName("afterCommit() avec registry non-null propage les rows à la registry")
    void afterCommitWithRegistry() throws IOException {
        Path merged    = Files.createFile(tmpDir.resolve("merged7.csv"));
        Path processed = Files.createDirectory(tmpDir.resolve("processed7"));

        DataRepository    repo    = mock(DataRepository.class);
        WorkflowTempCleanup cleanup = mock(WorkflowTempCleanup.class);
        StoreAllPathSink  sink    = mock(StoreAllPathSink.class);
        WorkflowActiveRegistry registry = mock(WorkflowActiveRegistry.class);
        when(repo.storeAll(any(), any(), any())).thenReturn(50L);

        MergeFileDeferredRunner r = new MergeFileDeferredRunner(
                repo, cleanup, sink,
                new MergeFileDeferredRunner.WorkflowContext(merged, processed, CORR_ID, registry),
                new MergeFileDeferredRunner.Callbacks(null, null, null));
        r.afterCommit();

        verify(sink).recordDeferredRowsWritten(50L);
    }
}