package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.BinaryFile;
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
class GetFileWithDataUseCaseTest {

    @Mock
    private BinaryFileService binaryFileService;

    private GetFileWithDataUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFileWithDataUseCase(binaryFileService);
    }

    @Test
    void execute_returnsFileWhenFound() {
        UUID fileId = UUID.randomUUID();
        BinaryFile file = mock(BinaryFile.class);

        when(binaryFileService.getFileWithData("app", fileId))
                .thenReturn(Optional.of(file));

        Optional<BinaryFile> result = useCase.execute("app", fileId);

        assertEquals(Optional.of(file), result);
    }

    @Test
    void execute_returnsEmptyWhenNotFound() {
        UUID fileId = UUID.randomUUID();

        when(binaryFileService.getFileWithData("app", fileId))
                .thenReturn(Optional.empty());

        Optional<BinaryFile> result = useCase.execute("app", fileId);

        assertEquals(Optional.empty(), result);
    }
}
