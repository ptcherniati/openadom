package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.filesenderclient.BuildBundleReport;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class WriteUploadBundleUseCaseTest {

    @Mock
    private DataService dataService;

    @InjectMocks
    private WriteUploadBundleUseCase useCase;

    @Test
    void execute_shouldDelegateToDataService() throws IOException {
        // Given
        String instanceUrl = "http://example.com";
        String nameOrId = "testApp";
        boolean withData = true;
        Locale locale = Locale.FRENCH;
        Path tempZipDirectory = Paths.get("/tmp/test");
        BuildBundleReport expected = new BuildBundleReport(null, List.of(), List.of(), List.of(), Locale.FRENCH);
        
        when(dataService.writeUploadBundle(instanceUrl, nameOrId, withData, locale, tempZipDirectory))
            .thenReturn(expected);

        // When
        BuildBundleReport result = useCase.execute(instanceUrl, nameOrId, withData, locale, tempZipDirectory);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(dataService).writeUploadBundle(instanceUrl, nameOrId, withData, locale, tempZipDirectory);
    }
}