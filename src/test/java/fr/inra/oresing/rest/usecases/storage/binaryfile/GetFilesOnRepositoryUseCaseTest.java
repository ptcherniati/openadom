package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetFilesOnRepositoryUseCaseTest {

    @Mock
    private BinaryFileService binaryFileService;

    private GetFilesOnRepositoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFilesOnRepositoryUseCase(binaryFileService);
    }

    @Test
    void execute_returnsFileList() {
        BinaryFile file1 = mock(BinaryFile.class);
        BinaryFile file2 = mock(BinaryFile.class);
        List<BinaryFile> expected = List.of(file1, file2);
        BinaryFileDataset dataset = mock(BinaryFileDataset.class);

        when(binaryFileService.getFilesOnRepository("app", "dataType", dataset, false))
                .thenReturn(expected);

        List<BinaryFile> result = useCase.execute("app", "dataType", dataset, false);

        assertEquals(expected, result);
    }
}
