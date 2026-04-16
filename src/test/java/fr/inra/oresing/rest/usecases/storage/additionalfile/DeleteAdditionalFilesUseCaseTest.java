package fr.inra.oresing.rest.usecases.storage.additionalfile;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.services.AdditionalFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class DeleteAdditionalFilesUseCaseTest {

    @Mock
    private AdditionalFileService additionalFileService;

    private DeleteAdditionalFilesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteAdditionalFilesUseCase(additionalFileService);
    }

    @Test
    void execute_returnsDeletedFileIds() throws BadAdditionalFileParamsSearchException {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> expected = List.of(id1, id2);
        AdditionalFilesInfos infos = new AdditionalFilesInfos();

        when(additionalFileService.deleteAdditionalFiles("app", infos))
                .thenReturn(expected);

        List<UUID> result = useCase.execute("app", infos);

        assertEquals(expected, result);
    }

    @Test
    void execute_returnsEmptyListWhenNoFilesDeleted() throws BadAdditionalFileParamsSearchException {
        List<UUID> expected = List.of();
        AdditionalFilesInfos infos = new AdditionalFilesInfos();

        when(additionalFileService.deleteAdditionalFiles("app", infos))
                .thenReturn(expected);

        List<UUID> result = useCase.execute("app", infos);

        assertEquals(expected, result);
    }
}
