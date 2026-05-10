package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.domain.authorization.CurrentUserRolesResult;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetAdminAuthorizationsUseCaseTest {

    @Mock
    private AuthenticationService authenticationService;

    private GetAdminAuthorizationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetAdminAuthorizationsUseCase(authenticationService);
    }

    @Test
    void execute_returnsAdminAuthorizationsList() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        CurrentUserRolesResult roles = new CurrentUserRolesResult(Map.of(), userId1, "user1", true, false, List.of(), false);
        
        LoginAdminResult result1 = new LoginAdminResult(userId1, "user1", "user1@test.com", "active", roles, Set.of(), Map.of());
        LoginAdminResult result2 = new LoginAdminResult(userId2, "user2", "user2@test.com", "active", roles, Set.of(), Map.of());
        List<LoginAdminResult> expected = List.of(result1, result2);

        when(authenticationService.getAdminAuthorizations())
                .thenReturn(expected);

        List<LoginAdminResult> result = useCase.execute();

        assertEquals(expected, result);
    }

    @Test
    void execute_returnsEmptyListWhenNoAdmins() {
        List<LoginAdminResult> expected = List.of();

        when(authenticationService.getAdminAuthorizations())
                .thenReturn(expected);

        List<LoginAdminResult> result = useCase.execute();

        assertEquals(expected, result);
    }
}
