package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires sur les methodes de suppression de
 * {@link WorkflowLogRepository} ( delete-one + purge-all ) . Pas de
 * Spring : on injecte un mock {@link JdbcTemplate} et on verifie le SQL
 * envoye + le binding parametre + le row-count retourne .
 *
 * <p>Cas couverts :
 * <ul>
 *   <li>{@code deleteByCorrelationId} : 1 row supprimee , 0 row ( id
 *       inconnu ) , null id ( no-op , aucun appel JDBC ) ;
 *   <li>{@code deleteAll} : N rows supprimees , 0 row ( table vide ) ;
 *   <li>verification des 2 SQL constants pour figer toute regression
 *       future ( changement de schema , typo ) .
 * </ul>
 *
 * @author R.YAHIAOUI
 */
class WorkflowLogRepositoryDeleteUnitTest {

    private static final String DELETE_BY_ID_SQL =
            "DELETE FROM oa_audit.workflow_log WHERE correlation_id = ?::uuid";
    private static final String DELETE_ALL_SQL =
            "DELETE FROM oa_audit.workflow_log";

    private JdbcTemplate jdbcTemplate;
    private WorkflowLogRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new WorkflowLogRepository(jdbcTemplate);
    }

    @Test
    void deleteByCorrelationId_existingId_returns1AndPassesUuidStringToJdbc() {
        UUID id = UUID.fromString("96a7ee3f-a9d9-43ff-93aa-3ecd31ca906f");
        when(jdbcTemplate.update(eq(DELETE_BY_ID_SQL), eq(id.toString()))).thenReturn(1);

        int deleted = repository.deleteByCorrelationId(id);

        assertEquals(1, deleted);
        // SQL must cast the param to ::uuid AND the param must be the UUID
        // string ( not the UUID object - PG driver would reject the binding
        // because the cast applies to a text parameter ) .
        verify(jdbcTemplate, times(1)).update(eq(DELETE_BY_ID_SQL), eq(id.toString()));
    }

    @Test
    void deleteByCorrelationId_unknownId_returns0() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update(eq(DELETE_BY_ID_SQL), eq(id.toString()))).thenReturn(0);

        int deleted = repository.deleteByCorrelationId(id);

        assertEquals(0, deleted);
        verify(jdbcTemplate).update(eq(DELETE_BY_ID_SQL), eq(id.toString()));
    }

    @Test
    void deleteByCorrelationId_nullId_returns0AndDoesNotHitJdbc() {
        int deleted = repository.deleteByCorrelationId(null);

        // Garde-fou : un null ne doit jamais atteindre la couche JDBC
        // ( eviterait un NPE PG mais aussi un DELETE involontaire si la
        // requete etait mal formee ) .
        assertEquals(0, deleted);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void deleteAll_returnsRowCountFromJdbc() {
        when(jdbcTemplate.update(eq(DELETE_ALL_SQL))).thenReturn(742);

        int deleted = repository.deleteAll();

        assertEquals(742, deleted);
        verify(jdbcTemplate, times(1)).update(eq(DELETE_ALL_SQL));
    }

    @Test
    void deleteAll_emptyTable_returns0() {
        when(jdbcTemplate.update(eq(DELETE_ALL_SQL))).thenReturn(0);

        int deleted = repository.deleteAll();

        assertEquals(0, deleted);
        verify(jdbcTemplate).update(eq(DELETE_ALL_SQL));
    }

    @Test
    void deleteAll_neverCallsDeleteByIdSql() {
        when(jdbcTemplate.update(eq(DELETE_ALL_SQL))).thenReturn(0);

        repository.deleteAll();

        // Garde-fou anti regression : deleteAll ne doit jamais emettre
        // la variante WHERE correlation_id = ? ( risque d'oubli du WHERE
        // = purge involontaire ) .
        verify(jdbcTemplate, never()).update(eq(DELETE_BY_ID_SQL), org.mockito.ArgumentMatchers.any(Object.class));
    }
}
