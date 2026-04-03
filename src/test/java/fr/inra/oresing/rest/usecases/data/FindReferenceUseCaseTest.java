package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.services.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class FindReferenceUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private DataService dataService;

    private FindReferenceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FindReferenceUseCase(applicationService, dataService);
    }

    @Test
    void execute_returnsDataValues() {
        DataValue value1 = mock(DataValue.class);
        DataValue value2 = mock(DataValue.class);
        List<DataValue> expected = List.of(value1, value2);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> params = mock(MultiValueMap.class);

        when(dataService.findReference("app", "refType", params))
                .thenReturn(expected);

        List<DataValue> result = useCase.execute("app", "refType", params);

        assertEquals(expected, result);
    }

    @Test
    void execute_returnsEmptyListWhenNoReferences() {
        List<DataValue> expected = List.of();
        @SuppressWarnings("unchecked")
        MultiValueMap<String, String> params = mock(MultiValueMap.class);

        when(dataService.findReference("app", "refType", params))
                .thenReturn(expected);

        List<DataValue> result = useCase.execute("app", "refType", params);

        assertEquals(expected, result);
    }
}
