package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DeleteFileRowCompensationHandler} ( audit P0-4 ) :
 * adaptateur de routing qui delegue au {@link OrphanBinaryFileCleaner} partage .
 */
@Tag("domain.model")
@DisplayName("DeleteFileRowCompensationHandler - delegation cleaner")
class DeleteFileRowCompensationHandlerTest {

    private static final UUID FILE_ID = UUID.randomUUID();

    private CompensationLogEntry entry(String schema) {
        return new CompensationLogEntry(
                UUID.randomUUID(), DeleteFileRowCompensationHandler.OP_TYPE,
                schema, "binaryfile", FILE_ID.toString(),
                UUID.randomUUID(), UUID.randomUUID(), "alice",
                Map.of(), "PENDING", Instant.now(), 60, 0, null, null);
    }

    @Test
    @DisplayName("operationType() retourne DELETE_FILE_ROW")
    void operationType() {
        OrphanBinaryFileCleaner cleaner = mock(OrphanBinaryFileCleaner.class);
        assertThat(new DeleteFileRowCompensationHandler(cleaner).operationType())
                .isEqualTo("DELETE_FILE_ROW");
    }

    @Test
    @DisplayName("compensate() delegue ( schema , fileId ) au cleaner")
    void delegatesToCleaner() {
        OrphanBinaryFileCleaner cleaner = mock(OrphanBinaryFileCleaner.class);
        new DeleteFileRowCompensationHandler(cleaner).compensate(entry("oa_data"));
        verify(cleaner).deleteIfOrphan("oa_data", FILE_ID);
    }
}
