package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.rest.data.VersioningService;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class UnPublishVersionBeforeDeleteUseCaseTest {

    @Mock
    private VersioningService versioningService;

    private UnPublishVersionBeforeDeleteUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UnPublishVersionBeforeDeleteUseCase(versioningService);
    }

    @Test
    void execute_returnsDataVersioningResult() throws Exception {
        UUID id = UUID.randomUUID();
        DataVersioningResult expected = mock(DataVersioningResult.class);

        when(versioningService.unPublishVersionBeforeDelete(Locale.ENGLISH, "app", id, true))
                .thenReturn(expected);

        DataVersioningResult result = useCase.execute(Locale.ENGLISH, "app", id, true);

        assertEquals(expected, result);
    }
}
