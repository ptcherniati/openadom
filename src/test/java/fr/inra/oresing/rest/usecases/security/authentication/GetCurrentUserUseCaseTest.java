package fr.inra.oresing.rest.usecases.security.authentication;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private GetCurrentUserUseCase useCase;

    @Test
    void execute_shouldDelegateToAuthenticationService() {
        // Given
        OreSiUser expected = new OreSiUser();
        when(authenticationService.getCurrentUser()).thenReturn(expected);

        // When
        OreSiUser result = useCase.execute();

        // Then
        assertThat(result).isEqualTo(expected);
        verify(authenticationService).getCurrentUser();
    }
}
