package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class RemoveFileUseCaseTest {

    @Mock
    private BinaryFileService binaryFileService;

    private RemoveFileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RemoveFileUseCase(binaryFileService);
    }

    @Test
    void execute_returnsFileIdWhenRemoved() {
        UUID fileId = UUID.randomUUID();
        Application app = mock(Application.class);

        when(binaryFileService.removeFile(app, fileId))
                .thenReturn(Optional.of(fileId));

        Optional<UUID> result = useCase.execute(app, fileId);

        assertEquals(Optional.of(fileId), result);
    }

    @Test
    void execute_returnsEmptyWhenNotFound() {
        UUID fileId = UUID.randomUUID();
        Application app = mock(Application.class);

        when(binaryFileService.removeFile(app, fileId))
                .thenReturn(Optional.empty());

        Optional<UUID> result = useCase.execute(app, fileId);

        assertEquals(Optional.empty(), result);
    }
}
