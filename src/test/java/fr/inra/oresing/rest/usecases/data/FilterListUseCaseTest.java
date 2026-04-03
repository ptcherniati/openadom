package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.FilterList;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class FilterListUseCaseTest {

    @Mock
    private DataService dataService;

    private FilterListUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FilterListUseCase(dataService);
    }

    @Test
    void execute_returnsFilterListFlux() {
        Application application = mock(Application.class);
        Flux<FilterList> expected = Flux.empty();

        when(dataService.filterList(application, "ref"))
                .thenReturn(expected);

        Flux<FilterList> result = useCase.execute(application, "ref");

        assertEquals(expected, result);
    }
}
