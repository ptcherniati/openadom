package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class ReadEntryUseCaseTest {

    @Mock
    private DataService dataService;

    @InjectMocks
    private ReadEntryUseCase useCase;

    @Test
    void execute_shouldDelegateToDataService() throws IOException {
        // Given
        File zipBundleFile = new File("/tmp/test.zip");
        String entryName = "manifest.json";
        Consumer<InputStream> consumer = mock(Consumer.class);

        // When
        useCase.execute(zipBundleFile, entryName, consumer);

        // Then
        verify(dataService).readEntry(zipBundleFile, entryName, consumer);
    }
}
