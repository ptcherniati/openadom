package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.model.authorization.UserAuthorizationForApplication;
import fr.inra.oresing.rest.services.ApplicationService;
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
class GetApplicationAuthorizationsUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private AuthenticationService authenticationService;

    private GetApplicationAuthorizationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetApplicationAuthorizationsUseCase(applicationService, authenticationService);
    }

    @Test
    void execute_returnsApplicationAuthorizationsList() {
        Application application = mock(Application.class);
        UserAuthorizationForApplication auth1 = mock(UserAuthorizationForApplication.class);
        UserAuthorizationForApplication auth2 = mock(UserAuthorizationForApplication.class);
        List<UserAuthorizationForApplication> expected = List.of(auth1, auth2);

        when(applicationService.getApplication("myApp"))
                .thenReturn(application);
        when(authenticationService.getApplicationAuthorizations(application))
                .thenReturn(expected);

        List<UserAuthorizationForApplication> result = useCase.execute("myApp");

        assertEquals(expected, result);
    }

    @Test
    void execute_returnsEmptyListWhenNoAuthorizations() {
        Application application = mock(Application.class);
        List<UserAuthorizationForApplication> expected = List.of();

        when(applicationService.getApplication("myApp"))
                .thenReturn(application);
        when(authenticationService.getApplicationAuthorizations(application))
                .thenReturn(expected);

        List<UserAuthorizationForApplication> result = useCase.execute("myApp");

        assertEquals(expected, result);
    }
}
