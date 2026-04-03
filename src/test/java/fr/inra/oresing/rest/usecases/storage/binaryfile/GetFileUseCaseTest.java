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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetFileUseCaseTest {

    @Mock
    private BinaryFileService binaryFileService;

    private GetFileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFileUseCase(binaryFileService);
    }

    @Test
    void execute_returnsFileWhenFound() {
        UUID fileId = UUID.randomUUID();
        BinaryFile file = mock(BinaryFile.class);
        Optional<BinaryFile> expected = Optional.of(file);

        when(binaryFileService.getFile("app", fileId))
                .thenReturn(expected);

        Optional<BinaryFile> result = useCase.execute("app", fileId);

        assertEquals(expected, result);
    }

    @Test
    void execute_returnsEmptyWhenNotFound() {
        UUID fileId = UUID.randomUUID();
        Optional<BinaryFile> expected = Optional.empty();

        when(binaryFileService.getFile("app", fileId))
                .thenReturn(expected);

        Optional<BinaryFile> result = useCase.execute("app", fileId);

        assertEquals(expected, result);
    }
}
