package fr.inra.oresing.monitoring.compensation.handlers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link OrphanBinaryFileCleaner} ( logique smart-delete
 * partagee par {@link BinaryFileCompensationHandler} et
 * {@link DeleteFileRowCompensationHandler} ) .
 *
 * <p>Couvre : schema invalide / injection / majuscules -> exception ;
 * referencevalue presentes -> DELETE SKIPPED ( regle d'or ) ; aucune
 * referencevalue -> DELETE execute ; binaryfile deja absente -> pas
 * d'exception .
 */
@Tag("domain.model")
@DisplayName("OrphanBinaryFileCleaner - regle d'or smart-delete binaryfile")
class OrphanBinaryFileCleanerTest {

    private static final UUID FILE_ID = UUID.randomUUID();

    @Test
    @DisplayName("schema null -> IllegalArgumentException")
    void schemaNull() {
        OrphanBinaryFileCleaner cleaner = new OrphanBinaryFileCleaner(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> cleaner.deleteIfOrphan(null, FILE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid target_schema");
    }

    @Test
    @DisplayName("schema avec injection SQL -> IllegalArgumentException")
    void schemaMalicious() {
        OrphanBinaryFileCleaner cleaner = new OrphanBinaryFileCleaner(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> cleaner.deleteIfOrphan("oa_data; DROP TABLE", FILE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("schema majuscules -> IllegalArgumentException ( SAFE_IDENT minuscules only )")
    void schemaMajuscules() {
        OrphanBinaryFileCleaner cleaner = new OrphanBinaryFileCleaner(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> cleaner.deleteIfOrphan("OA_DATA", FILE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("referencevalue rows presentes -> DELETE binaryfile SKIPPED ( regle d'or )")
    void regleOrGardeLesBinaryfile() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("referencevalue"), eq(Boolean.class), any()))
                .thenReturn(Boolean.TRUE);

        new OrphanBinaryFileCleaner(jdbc).deleteIfOrphan("oa_data", FILE_ID);

        verify(jdbc, never()).update(contains("DELETE FROM oa_data.binaryfile"), (Object) any());
    }

    @Test
    @DisplayName("aucune referencevalue -> DELETE binaryfile execute")
    void noReferenceValueDeleteExecuted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("referencevalue"), eq(Boolean.class), any()))
                .thenReturn(Boolean.FALSE);
        when(jdbc.update(any(String.class), (Object) any())).thenReturn(1);

        new OrphanBinaryFileCleaner(jdbc).deleteIfOrphan("oa_data", FILE_ID);

        verify(jdbc).update(contains("DELETE FROM oa_data.binaryfile"), (Object) any());
    }

    @Test
    @DisplayName("binaryfile deja absente ( deleted=0 ) -> pas d'exception")
    void alreadyAbsentNoException() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("referencevalue"), eq(Boolean.class), any()))
                .thenReturn(Boolean.FALSE);
        when(jdbc.update(any(String.class), (Object) any())).thenReturn(0);

        new OrphanBinaryFileCleaner(jdbc).deleteIfOrphan("oa_data", FILE_ID);

        verify(jdbc).update(contains("DELETE FROM oa_data.binaryfile"), (Object) any());
    }
}
