package fr.inra.oresing.rest.usecases.storage.binaryfile;

import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.data.VersioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class CreateDataUseCaseTest {

    @Mock
    private VersioningService versioningService;

    private CreateDataUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateDataUseCase(versioningService);
    }

    @Test
    void execute_returnsDataVersioningResult() throws IOException {
        DataVersioningResult expected = mock(DataVersioningResult.class);
        DataFile file = mock(DataFile.class);
        Locale locale = Locale.FRENCH;

        when(versioningService.createData(locale, "app", "dataName", file, false, false))
                .thenReturn(expected);

        DataVersioningResult result = useCase.execute(locale, "app", "dataName", file, false, false);

        assertEquals(expected, result);
    }
}
