package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.DataWriter;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.data.VersioningService;
import fr.inra.oresing.rest.data.publication.StoreFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetStoreFileUseCaseTest {

    @Mock
    private VersioningService versioningService;

    private GetStoreFileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetStoreFileUseCase(versioningService);
    }

    @Test
    void execute_returnsStoreFile() {
        Application app = mock(Application.class);
        UUID fileId = UUID.randomUUID();
        FileOrUUID fileOrUUID = new FileOrUUID(fileId, null, true);
        DataWriter dataWriter = mock(DataWriter.class);
        StoreFile expected = mock(StoreFile.class);

        when(versioningService.getStoreFile(app, "dataName", fileOrUUID, null, dataWriter))
                .thenReturn(expected);

        StoreFile result = useCase.execute(app, "dataName", fileOrUUID, null, dataWriter);

        assertEquals(expected, result);
    }
}
