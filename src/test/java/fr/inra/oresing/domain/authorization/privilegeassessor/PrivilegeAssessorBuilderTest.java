package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdministratorForSystemException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link PrivilegeAssessorBuilder}.
 * Valide la garde de {@code forSystem()} et le comportement sans garde des autres méthodes.
 */
@Tag("core.auth")
@DisplayName("Tests unitaires de PrivilegeAssessorBuilder")
class PrivilegeAssessorBuilderTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AuthorizationsForSystemUser systemAuthWith(boolean isOpenAdomAdmin, Set<String> creators) {
        CurrentUserRoles roles = mock(CurrentUserRoles.class);
        when(roles.isOpenAdomAdmin()).thenReturn(isOpenAdomAdmin);
        AuthorizationsForSystemUser auth = mock(AuthorizationsForSystemUser.class);
        when(auth.currentUserRoles()).thenReturn(roles);
        when(auth.applicationCreator()).thenReturn(creators);
        return auth;
    }

    // ── forSystem ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("forSystem() sans droits → lève NotOpenAdomAdministratorForSystemException")
    void forSystem_noRights_throwsException() {
        AuthorizationsForSystemUser auth = systemAuthWith(false, Set.of());
        assertThrows(
                NotOpenAdomAdministratorForSystemException.class,
                () -> PrivilegeAssessorBuilder.forSystem(auth, PrivilegeSystemDomainEnum.SYSTEM_ADMINISTRATION),
                "forSystem() doit lever NotOpenAdomAdministratorForSystemException sans droits"
        );
    }

    @Test
    @DisplayName("forSystem() avec isOpenAdomAdmin=true → retourne un assessor non null")
    void forSystem_openAdomAdmin_returnsAssessor() {
        AuthorizationsForSystemUser auth = systemAuthWith(true, Set.of());
        PrivilegeAssessorDomainForSystem<?> assessor =
                PrivilegeAssessorBuilder.forSystem(auth, PrivilegeSystemDomainEnum.SYSTEM_ADMINISTRATION);
        assertNotNull(assessor);
    }

    @Test
    @DisplayName("forSystem() avec applicationCreator non vide (sans admin) → retourne un assessor")
    void forSystem_creatorNotEmpty_returnsAssessor() {
        AuthorizationsForSystemUser auth = systemAuthWith(false, Set.of("some-app-pattern"));
        PrivilegeAssessorDomainForSystem<?> assessor =
                PrivilegeAssessorBuilder.forSystem(auth, PrivilegeSystemDomainEnum.SYSTEM_ADMINISTRATION);
        assertNotNull(assessor);
    }

    // ── forUser ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("forUser() sans droits → retourne un assessor (pas de garde)")
    void forUser_noRights_returnsAssessor() {
        AuthorizationsForSystemUser auth = systemAuthWith(false, Set.of());
        PrivilegeAssessorDomainForSystem<?> assessor =
                PrivilegeAssessorBuilder.forUser(auth, PrivilegeSystemDomainEnum.SYSTEM_USER_CONNECTED);
        assertNotNull(assessor);
    }

    @Test
    @DisplayName("forUser() avec isOpenAdomAdmin=true → retourne un assessor")
    void forUser_openAdomAdmin_returnsAssessor() {
        AuthorizationsForSystemUser auth = systemAuthWith(true, Set.of());
        PrivilegeAssessorDomainForSystem<?> assessor =
                PrivilegeAssessorBuilder.forUser(auth, PrivilegeSystemDomainEnum.SYSTEM_USER_CONNECTED);
        assertNotNull(assessor);
    }

    // ── forApplication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("forApplication() → retourne toujours un assessor (pas de garde)")
    void forApplication_alwaysReturnsAssessor() {
        AuthorizationsForApplicationUser appAuth = mock(AuthorizationsForApplicationUser.class);
        GetGrantableResult grantable = mock(GetGrantableResult.class);
        var application = mock(fr.inra.oresing.domain.application.Application.class);

        PrivilegeAssessorDomainForApplication<?> assessor = PrivilegeAssessorBuilder.forApplication(
                appAuth,
                PrivilegeApplicationDomainEnum.APPLICATION_MANAGER,
                application,
                grantable
        );
        assertNotNull(assessor);
    }
}