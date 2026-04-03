package fr.inra.oresing.rest.usecases.storage.additionalfile;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.zip.ZipOutputStream;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetAdditionalFilesZipStreamUseCaseTest {

    @Mock
    private AdditionalFileService additionalFileService;

    private GetAdditionalFilesZipStreamUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAdditionalFilesZipStreamUseCase(additionalFileService);
    }

    @Test
    void execute_callsServiceGetZipStream() throws Exception {
        ZipOutputStream zipOut = mock(ZipOutputStream.class);
        AdditionalFilesInfos infos = new AdditionalFilesInfos();

        useCase.execute(zipOut, "app", infos);

        verify(additionalFileService).getAdditionalFilesNamesZipStream(zipOut, "app", infos);
    }
}
