package fr.inra.oresing.workflow.cascade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests pour la garde de progression monotone du UPSERT batched dans
 * {@link StagingFinalizeSql} ( CR-3 audit cascade-integration ) .
 */
@DisplayName("StagingFinalizeSql UPSERT loop")
class StagingFinalizeSqlTest {

    @Test
    @DisplayName("aborte la boucle si staging table ne retrecit pas malgre affected > 0")
    void abortsWhenStagingDoesNotShrink() throws Exception {
        Connection conn = mock(Connection.class);

        // PreparedStatement pour le UPSERT batch ( executeUpdate )
        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(50_000);

        // PreparedStatement pour SELECT COUNT(*) sur staging
        PreparedStatement countPs = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        // Staging stagne : retourne 100k a chaque appel ( pathologique )
        when(rs.getLong(1)).thenReturn(100_000L);
        when(countPs.executeQuery()).thenReturn(rs);

        // Routage prepareStatement : "SELECT COUNT" -> countPs , reste -> upsertPs et autres
        PreparedStatement deleteRefRefPs = mock(PreparedStatement.class);
        PreparedStatement insertRefRefPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.startsWith("SELECT COUNT(*)")) return countPs;
            if (sql.startsWith("DELETE FROM"))    return deleteRefRefPs;
            if (sql.startsWith("INSERT INTO") && sql.contains("reference_reference"))
                                                  return insertRefRefPs;
            return upsertPs;                                                  // batch UPSERT
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StagingFinalizeSql.runFinalize(
                        conn, "ticket_507", "referencevalue",
                        new String[] { "id", "data" },
                        "referencevalue_import",
                        UUID.randomUUID().toString(),
                        "id"));
        assertTrue(ex.getMessage().contains("did not shrink"),
                "Expected shrink-failure error message , got : " + ex.getMessage());
    }

    @Test
    @DisplayName("complete normalement quand staging retrecit jusqu'a 0")
    void completesWhenStagingShrinksToZero() throws Exception {
        Connection conn = mock(Connection.class);

        PreparedStatement upsertPs = mock(PreparedStatement.class);
        when(upsertPs.executeUpdate()).thenReturn(50_000);

        PreparedStatement countPs = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        // Staging diminue : 150k -> 100k -> 50k -> 0 ( exit normal )
        when(rs.getLong(1)).thenReturn(150_000L, 100_000L, 50_000L, 0L);
        when(countPs.executeQuery()).thenReturn(rs);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.startsWith("SELECT COUNT(*)")) return countPs;
            if (sql.contains("WITH batch AS"))     return upsertPs;            // batch UPSERT
            return otherPs;                                                    // delete / insert ref_ref
        });

        // Pas d'exception attendue
        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                UUID.randomUUID().toString(),
                "id");

        // 3 batches ( 150k -> 100k -> 50k -> 0 ) puis exit
        verify(upsertPs, times(3)).executeUpdate();
    }

    @Test
    @DisplayName("skip la boucle si staging deja vide en entree")
    void skipsLoopIfStagingEmpty() throws Exception {
        Connection conn = mock(Connection.class);

        PreparedStatement countPs = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(1)).thenReturn(0L);                                   // staging vide
        when(countPs.executeQuery()).thenReturn(rs);

        PreparedStatement otherPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.startsWith("SELECT COUNT(*)")) return countPs;
            return otherPs;
        });

        StagingFinalizeSql.runFinalize(
                conn, "ticket_507", "referencevalue",
                new String[] { "id", "data" },
                "referencevalue_import",
                null,                                                         // PER_CONNECTION_TEMP
                "id");

        // Aucune iteration UPSERT ( staging deja vide )
        verify(countPs, atLeastOnce()).executeQuery();
    }
}
