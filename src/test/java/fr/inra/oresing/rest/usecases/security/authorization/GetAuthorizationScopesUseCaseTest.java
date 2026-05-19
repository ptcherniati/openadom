package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import fr.inra.oresing.rest.services.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetAuthorizationScopesUseCaseTest {

    @Mock
    private AuthorizationService authorizationService;

    private GetAuthorizationScopesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAuthorizationScopesUseCase(authorizationService);
    }

    @Test
    void execute_returnsScopes() {
        Application application = mock(Application.class);
        Map<String, List<GetGrantableResult.ReferenceScope>> expected = mock(Map.class);

        when(authorizationService.getAuthorizationScopes(application, MenuType.submission))
                .thenReturn(expected);

        Map<String, List<GetGrantableResult.ReferenceScope>> result = useCase.execute(application, MenuType.submission);

        assertEquals(expected, result);
    }
}
