package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryNoFilter;
import fr.inra.oresing.domain.data.read.query.OutPut;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class FindDataUseCaseTest {

    @Mock
    private DataService dataService;

    private FindDataUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FindDataUseCase(dataService);
    }

    @Test
    void execute_returnsDataRows() {
        Application application = mock(Application.class);
        DownloadDatasetQuery query = new DownloadDatasetQueryNoFilter(
            application,
            "data",
            new OutPut(Locale.ENGLISH, 0L, 0L),
            Set.of(),
            Set.of(),
            false
        );
        List<DataRow> expected = mock(List.class);

        when(dataService.findData(query))
                .thenReturn(expected);

        List<DataRow> result = useCase.execute(query);

        assertEquals(expected, result);
    }
}
