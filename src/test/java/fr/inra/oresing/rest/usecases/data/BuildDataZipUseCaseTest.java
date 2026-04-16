package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryNoFilter;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.verify;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class BuildDataZipUseCaseTest {

    @Mock
    private DataService dataService;

    @InjectMocks
    private BuildDataZipUseCase useCase;

    @Test
    void execute_shouldDelegateToDataService() {
        // Given
        Path tempDirectory = Paths.get("/tmp/test");
        DownloadDatasetQueryNoFilter query = new DownloadDatasetQueryNoFilter(null, "dataType", null, null, null, false);

        // When
        useCase.execute(tempDirectory, query);

        // Then
        verify(dataService).buildDataZip(tempDirectory, query);
    }
}
