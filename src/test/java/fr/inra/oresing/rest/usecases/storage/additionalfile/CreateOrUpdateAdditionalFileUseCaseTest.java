package fr.inra.oresing.rest.usecases.storage.additionalfile;

import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class CreateOrUpdateAdditionalFileUseCaseTest {

    @Mock
    private AdditionalFileService additionalFileService;

    private CreateOrUpdateAdditionalFileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrUpdateAdditionalFileUseCase(additionalFileService);
    }

    @Test
    void execute_returnsFileUuid() {
        UUID expected = UUID.randomUUID();
        CreateAdditionalFileRequest request = mock(CreateAdditionalFileRequest.class);
        MultipartFile file = mock(MultipartFile.class);

        when(additionalFileService.createOrUpdate(request, "fileName", "app", file))
                .thenReturn(expected);

        UUID result = useCase.execute(request, "fileName", "app", file);

        assertEquals(expected, result);
    }
}
