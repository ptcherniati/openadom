package fr.inra.oresing.rest.usecases.storage.additionalfile;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.rightsrequest.GetAdditionalFilesResult;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class FindAdditionalFileUseCaseTest {

    @Mock
    private AdditionalFileService additionalFileService;

    private FindAdditionalFileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FindAdditionalFileUseCase(additionalFileService);
    }

    @Test
    void execute_returnsAdditionalFilesResult() {
        GetAdditionalFilesResult expected = mock(GetAdditionalFilesResult.class);
        AdditionalFilesInfos infos = mock(AdditionalFilesInfos.class);

        when(additionalFileService.findAdditionalFile("app", infos))
                .thenReturn(expected);

        GetAdditionalFilesResult result = useCase.execute("app", infos);

        assertEquals(expected, result);
    }
}
