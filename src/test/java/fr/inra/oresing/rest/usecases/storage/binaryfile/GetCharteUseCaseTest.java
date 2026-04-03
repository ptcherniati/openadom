package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.services.AdditionalFileService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetCharteUseCaseTest {

    @Mock
    private AdditionalFileService additionalFileService;

    private GetCharteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCharteUseCase(additionalFileService);
    }

    @Test
    void execute_callsServiceGetCharte() throws Exception {
        OutputStream out = mock(OutputStream.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AdditionalFilesInfos infos = new AdditionalFilesInfos();

        useCase.execute(out, response, "app", infos);

        verify(additionalFileService).getCharte(out, response, "app", infos);
    }
}
