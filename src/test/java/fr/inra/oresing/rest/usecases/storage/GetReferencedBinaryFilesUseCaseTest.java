package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetReferencedBinaryFilesUseCaseTest {

    @Mock
    private BinaryFileService binaryFileService;

    private GetReferencedBinaryFilesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetReferencedBinaryFilesUseCase(binaryFileService);
    }

    @Test
    void execute_returnsReferencedFiles() {
        UUID appId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        ReferencedBinaryFiles ref1 = mock(ReferencedBinaryFiles.class);
        List<ReferencedBinaryFiles> expected = List.of(ref1);

        when(binaryFileService.getReferencedBinaryFiles(appId, "dataType", Set.of(fileId)))
                .thenReturn(expected);

        List<ReferencedBinaryFiles> result = useCase.execute(appId, "dataType", Set.of(fileId));

        assertEquals(expected, result);
    }
}
