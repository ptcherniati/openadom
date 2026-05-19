package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.services.DefaultAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetAllUsersUseCaseTest {

    @Mock
    private DefaultAuthorizationService authorizationService;

    private GetAllUsersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAllUsersUseCase(authorizationService);
    }

    @Test
    void execute_returnsUsers() {
        List<OreSiUser> expected = mock(List.class);

        when(authorizationService.getAllUsers())
                .thenReturn(expected);

        List<OreSiUser> result = useCase.execute();

        assertEquals(expected, result);
    }
}