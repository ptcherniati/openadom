package fr.inra.oresing.workflow.cascade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests pour la boucle UPSERT batched dans {@link StagingFinalizeSql} .
 *
 * <p>Phase A L2 : la boucle est pilotee par {@code affected} ( aucun
 * {@code SELECT COUNT(*)} par iteration ) :
 * <ul>
 *   <li>{@code affected == 0} -&gt; staging epuise , exit normal ;</li>
 *   <li>{@code affected < BULK_INSERT_BATCH_SIZE} -&gt; derniere batch
 *       partielle , staging epuise , exit normal ;</li>
 *   <li>{@code batchNum > 100_000} -&gt; pathologique , throw .</li>
 * </ul>
 */
@DisplayName("StagingFinalizeSql UPSERT loop")
class StagingFinalizeSqlTest {

    /**
     * Mock {@code connection.createStatement()} pour que les CREATE TEMP /
     * Statement.execute appels du fix " ordre reference_reference " ne
     * NPE pas dans les tests . Retourne un Statement mock qui ne fait
     * rien sur execute / close .
     */
    private static void wireCreateStatementMock(Connection conn) throws Exception {
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
    }

    @Test
    @DisplayName("complete quand derniere batch est partielle ( affected < BATCH_SIZE )")
    void completesWhenLastBatchIsPartial() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        // 3 batches : 50k , 50k , 30k ( derniere partielle ) puis exit
        when(upsertPs.executeUpdate()).thenReturn(50_000, 50_000, 30_000);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                UUID.randomUUID().toString(),
                "id",
                0);

        // Verifie 3 executions du batch : 50k , 50k , 30k -> exit ( 30k < 50k )
        verify(upsertPs, times(3)).executeUpdate();
    }

    @Test
    @DisplayName("complete quand affected == 0 ( staging epuise pile )")
    void completesWhenAffectedHitsZero() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        // 2 batches a 50k , puis 0 ( pile epuise apres 2 batches complets )
        when(upsertPs.executeUpdate()).thenReturn(50_000, 50_000, 0);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                UUID.randomUUID().toString(),
                "id",
                0);

        // 3 calls : 50k , 50k , 0 -> exit on zero
        verify(upsertPs, times(3)).executeUpdate();
    }

    @Test
    @DisplayName("exit immediat quand staging deja vide ( affected==0 au 1er essai )")
    void completesImmediatelyWhenStagingEmpty() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(0);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,
                "id",
                0);

        // 1 seul executeUpdate -> 0 -> exit
        verify(upsertPs, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("aborte si la boucle depasse 100k iterations ( garde-fou anti-infinite-loop )")
    void abortsWhenExceedsMaxBatches() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        // Toujours BATCH_SIZE plein -> jamais de condition d'exit naturelle
        when(upsertPs.executeUpdate()).thenReturn(50_000);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StagingFinalizeSql.runFinalize(
                        conn, "ticket_507", "referencevalue",
                        new String[] { "id", "data" },
                        "referencevalue_import",
                        UUID.randomUUID().toString(),
                        "id",
                        0));
        assertTrue(ex.getMessage().contains("exceeded 100000 batches"),
                "Expected max-batches abort message , got : " + ex.getMessage());
    }

    @Test
    @DisplayName("emet SET LOCAL statement_timeout quand timeout > 0")
    void emitsStatementTimeoutWhenConfigured() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(0);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sqlCaptor.capture())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,
                "id",
                180);

        boolean foundTimeout = sqlCaptor.getAllValues().stream()
                .anyMatch(s -> s.contains("SET LOCAL statement_timeout = '180min'"));
        assertTrue(foundTimeout,
                "Expected SET LOCAL statement_timeout = '180min' , got : " + sqlCaptor.getAllValues());
    }

    @Test
    @DisplayName("ne pas emettre SET LOCAL statement_timeout quand timeout = 0 ( opt-out )")
    void skipsStatementTimeoutWhenZero() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(0);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sqlCaptor.capture())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,
                "id",
                0);

        boolean noTimeout = sqlCaptor.getAllValues().stream()
                .noneMatch(s -> s.contains("statement_timeout"));
        assertTrue(noTimeout,
                "Expected no SET LOCAL statement_timeout , got : " + sqlCaptor.getAllValues());
    }

    @Test
    @DisplayName("Phase B L1 : emet ANALYZE staging table avant la boucle UPSERT")
    void emitsAnalyzeOnStagingTableBeforeLoop() throws Exception {
        Connection conn = mock(Connection.class);
        // Capture executeStatement calls pour verifier ANALYZE emis
        Statement stmt = mock(Statement.class);
        ArgumentCaptor<String> stmtCaptor = ArgumentCaptor.forClass(String.class);
        when(stmt.execute(stmtCaptor.capture())).thenReturn(true);
        when(conn.createStatement()).thenReturn(stmt);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(0);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,
                "id",
                0);

        boolean foundAnalyze = stmtCaptor.getAllValues().stream()
                .anyMatch(s -> s.startsWith("ANALYZE ") && s.contains("referencevalue_import"));
        assertTrue(foundAnalyze,
                "Expected ANALYZE on staging table , got : " + stmtCaptor.getAllValues());
    }

    // ============================================================
    // Robustness layer 1 + 2 + 5 : lock_timeout SET LOCAL + retry SQLSTATE
    // 55P03 + enriched error message . Voir StagingFinalizeSql Javadoc .
    // ============================================================

    @Test
    @DisplayName("layer 1 : emet SET LOCAL lock_timeout en debut de finalize")
    void emitsSetLocalLockTimeout() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(0);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sqlCaptor.capture())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return mock(PreparedStatement.class);
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,
                "id",
                0);

        boolean hasLockTimeout = sqlCaptor.getAllValues().stream()
                .anyMatch(s -> s.contains("SET LOCAL lock_timeout"));
        assertTrue(hasLockTimeout,
                "Expected SET LOCAL lock_timeout , got : " + sqlCaptor.getAllValues());
    }

    /**
     * Helper : reduit le backoff initial a 1ms pour des tests rapides , puis
     * restaure le default au finally . Garantit que les tests de retry ne
     * font pas attendre toute la suite ( 7s+ cumule par defaut ) .
     */
    private static void runWithFastBackoff(Runnable test) {
        try {
            // Inject 1ms backoff via setter ( pas via JVM prop puisque la conf
            // est maintenant pilotee par ImportProperties supplier ) .
            StagingFinalizeSql.setLockRetryBackoffInitialMsSupplier(() -> 1L);
            StagingFinalizeSql.setLockRetryBackoffMaxMsSupplier(() -> 10L);
            test.run();
        } finally {
            // Restore defauts ( valeurs originales d'ImportProperties )
            StagingFinalizeSql.setLockRetryBackoffInitialMsSupplier(() -> 1000L);
            StagingFinalizeSql.setLockRetryBackoffMaxMsSupplier(() -> 60_000L);
        }
    }

    @Test
    @DisplayName("layer 2 : retry success apres 1 SQLSTATE 55P03 puis succes")
    void retriesAfterLockTimeoutThenSucceeds() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);
        // SAVEPOINT support
        java.sql.Savepoint sp = mock(java.sql.Savepoint.class);
        when(conn.setSavepoint(anyString())).thenReturn(sp);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        // 1ere attempt : 55P03 -> retry ; 2eme : 10 rows -> exit ( affected < batch size )
        java.sql.SQLException lockTimeoutEx =
                new java.sql.SQLException("canceling statement due to lock timeout", "55P03");
        when(upsertPs.executeUpdate())
                .thenThrow(lockTimeoutEx)
                .thenReturn(10);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        runWithFastBackoff(() -> {
            try {
                StagingFinalizeSql.runFinalize(
                        conn, "ticket_507", "referencevalue",
                        new String[] { "id", "data" },
                        "referencevalue_import",
                        UUID.randomUUID().toString(),
                        "id",
                        0);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        });

        // Verifie 2 executeUpdate ( 1 fail + 1 success ) , 1 rollback , 1 release
        verify(upsertPs, times(2)).executeUpdate();
        verify(conn, atLeastOnce()).rollback(sp);
        verify(conn, atLeastOnce()).releaseSavepoint(sp);
    }

    @Test
    @DisplayName("layer 2 : retry deadlock SQLSTATE 40P01 ( retriable )")
    void retriesAfterDeadlockDetectedThenSucceeds() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);
        java.sql.Savepoint sp = mock(java.sql.Savepoint.class);
        when(conn.setSavepoint(anyString())).thenReturn(sp);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        java.sql.SQLException deadlockEx =
                new java.sql.SQLException("deadlock detected", "40P01");
        when(upsertPs.executeUpdate())
                .thenThrow(deadlockEx)
                .thenReturn(5);  // < batch size -> exit

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        runWithFastBackoff(() -> {
            try {
                StagingFinalizeSql.runFinalize(
                        conn, "ticket_507", "referencevalue",
                        new String[] { "id", "data" },
                        "referencevalue_import",
                        UUID.randomUUID().toString(),
                        "id",
                        0);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        });

        verify(upsertPs, times(2)).executeUpdate();
    }

    @Test
    @DisplayName("layer 2 : abandonne apres max retries epuises ( 55P03 persistant )")
    void givesUpAfterMaxRetriesExhausted() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);
        java.sql.Savepoint sp = mock(java.sql.Savepoint.class);
        when(conn.setSavepoint(anyString())).thenReturn(sp);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        java.sql.SQLException lockTimeoutEx =
                new java.sql.SQLException("canceling statement due to lock timeout", "55P03");
        when(upsertPs.executeUpdate()).thenThrow(lockTimeoutEx);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        runWithFastBackoff(() -> {
            java.sql.SQLException thrown = assertThrows(java.sql.SQLException.class, () ->
                    StagingFinalizeSql.runFinalize(
                            conn, "ticket_507", "referencevalue",
                            new String[] { "id", "data" },
                            "referencevalue_import",
                            UUID.randomUUID().toString(),
                            "id",
                            0));
            // Layer 5 : message enrichi avec SQLSTATE , batch , target , retries
            assertTrue(thrown.getMessage().contains("SQLSTATE 55P03"),
                    "expected enriched message contains SQLSTATE , got : " + thrown.getMessage());
            assertTrue(thrown.getMessage().contains("batch #1"),
                    "expected message contains batch number , got : " + thrown.getMessage());
            assertTrue(thrown.getMessage().contains("target=referencevalue"),
                    "expected message contains target , got : " + thrown.getMessage());
            assertTrue(thrown.getMessage().contains("retries="),
                    "expected message contains retries count , got : " + thrown.getMessage());
        });

        // 1 initial + 3 retries = 4 tentatives par defaut
        verify(upsertPs, times(4)).executeUpdate();
    }

    @Test
    @DisplayName("layer 2 : SQLSTATE non-retriable ( != 55P03 ) propage sans retry")
    void doesNotRetryOnNonRetriableSqlState() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);
        java.sql.Savepoint sp = mock(java.sql.Savepoint.class);
        when(conn.setSavepoint(anyString())).thenReturn(sp);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        // 23505 = unique_violation , pas retriable
        java.sql.SQLException uniqueViolEx =
                new java.sql.SQLException("duplicate key", "23505");
        when(upsertPs.executeUpdate()).thenThrow(uniqueViolEx);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        java.sql.SQLException thrown = assertThrows(java.sql.SQLException.class, () ->
                StagingFinalizeSql.runFinalize(
                        conn, "ticket_507", "referencevalue",
                        new String[] { "id", "data" },
                        "referencevalue_import",
                        UUID.randomUUID().toString(),
                        "id",
                        0));
        // Message enrichi inclut SQLSTATE 23505 ( pas 55P03 )
        assertTrue(thrown.getMessage().contains("SQLSTATE 23505"),
                "expected enriched message contains 23505 , got : " + thrown.getMessage());
        // Pas de retry : 1 seule execution
        verify(upsertPs, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("Phase D F-1 : ne pas emettre SET LOCAL synchronous_commit ( cluster=off plus permissif )")
    void doesNotEmitSynchronousCommit() throws Exception {
        Connection conn = mock(Connection.class);
        wireCreateStatementMock(conn);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(0);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sqlCaptor.capture())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("WITH batch AS")) return upsertPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,
                "id",
                0);

        boolean noSyncCommit = sqlCaptor.getAllValues().stream()
                .noneMatch(s -> s.contains("synchronous_commit"));
        assertTrue(noSyncCommit,
                "Expected no SET LOCAL synchronous_commit , got : " + sqlCaptor.getAllValues());
    }
}