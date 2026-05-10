package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link BinaryFileCompensationHandler}.
 *
 * <p>Couvre :
 * <ul>
 *   <li>schema invalide → IllegalArgumentException</li>
 *   <li>referencevalue rows présentes → DELETE binaryfile SKIPPED (règle d'or)</li>
 *   <li>aucune referencevalue → DELETE binaryfile exécuté</li>
 *   <li>binaryfile déjà absent → log uniquement</li>
 *   <li>operationType() = IMPORT_BINARYFILE</li>
 * </ul>
 */
@Tag("domain.model")
@DisplayName("BinaryFileCompensationHandler — règle d'or DELETE binaryfile")
class BinaryFileCompensationHandlerTest {

    private static final UUID FILE_ID = UUID.randomUUID();

    private CompensationLogEntry entryWithSchema(String schema) {
        return new CompensationLogEntry(
                UUID.randomUUID(),
                BinaryFileCompensationHandler.OP_TYPE,
                schema,
                "binaryfile",
                FILE_ID.toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "alice",
                Map.of(),
                "PENDING",
                Instant.now(),
                60,
                0, null, null);
    }

    @Test
    @DisplayName("operationType() retourne IMPORT_BINARYFILE")
    void operationType() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        assertThat(handler.operationType()).isEqualTo("IMPORT_BINARYFILE");
    }

    @Test
    @DisplayName("schema null → IllegalArgumentException")
    void schemaNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        assertThatThrownBy(() -> handler.compensate(entryWithSchema(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid target_schema");
    }

    @Test
    @DisplayName("schema avec caractères invalides (SQL injection) → IllegalArgumentException")
    void schemaMalicious() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        assertThatThrownBy(() -> handler.compensate(entryWithSchema("oa_data; DROP TABLE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("schema valide mais référencevalue rows présentes → SKIP (règle d'or)")
    void regleOrGardeLesBinaryfile() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Simule: il existe des rows referencevalue
        when(jdbc.queryForObject(
                contains("referencevalue"),
                eq(Boolean.class),
                any()))
                .thenReturn(Boolean.TRUE);

        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        handler.compensate(entryWithSchema("oa_data"));

        // DELETE binaryfile ne doit PAS être appelé
        verify(jdbc, never()).update(contains("DELETE FROM oa_data.binaryfile"), (Object) any());
    }

    @Test
    @DisplayName("aucune referencevalue → DELETE binaryfile exécuté")
    void noReferenceValueDeleteExecuted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                contains("referencevalue"),
                eq(Boolean.class),
                any()))
                .thenReturn(Boolean.FALSE);
        when(jdbc.update(any(String.class), (Object) any())).thenReturn(1);

        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        handler.compensate(entryWithSchema("oa_data"));

        verify(jdbc).update(contains("DELETE FROM oa_data.binaryfile"), (Object) any());
    }

    @Test
    @DisplayName("binaryfile déjà absent (deleted=0) → pas d'exception, log only")
    void alreadyAbsentNoException() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                contains("referencevalue"),
                eq(Boolean.class),
                any()))
                .thenReturn(Boolean.FALSE);
        when(jdbc.update(any(String.class), (Object) any())).thenReturn(0);

        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        // Ne doit pas lever d'exception
        handler.compensate(entryWithSchema("oa_data"));
        verify(jdbc).update(contains("DELETE FROM oa_data.binaryfile"), (Object) any());
    }

    @Test
    @DisplayName("schema avec majuscules → IllegalArgumentException (SAFE_IDENT ne tolère pas)")
    void schemaMajuscules() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        assertThatThrownBy(() -> handler.compensate(entryWithSchema("OA_DATA")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("schema alphanumérique en minuscules → valide")
    void schemaAlphanumerique() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Boolean.class), any()))
                .thenReturn(Boolean.FALSE);
        when(jdbc.update(any(String.class), (Object) any())).thenReturn(1);

        BinaryFileCompensationHandler handler = new BinaryFileCompensationHandler(jdbc);
        // Ne doit pas lever d'exception
        handler.compensate(entryWithSchema("schema_123_valid"));
        verify(jdbc).update(any(String.class), (Object) any());
    }
}
