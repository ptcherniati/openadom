package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.util.Locale;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetDataCsvStreamUseCaseTest {

    @Mock
    private DataService dataService;

    private GetDataCsvStreamUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetDataCsvStreamUseCase(dataService);
    }

    @Test
    void execute_callsService() {
        OutputStream outputStream = OutputStream.nullOutputStream();

        useCase.execute(outputStream, "app", "data", Locale.ENGLISH, false);

        verify(dataService).getDataCsvStream(outputStream, "app", "data", Locale.ENGLISH, false);
    }
}
