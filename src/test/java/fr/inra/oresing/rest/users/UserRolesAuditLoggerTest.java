package fr.inra.oresing.rest.users;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests pour {@link UserRolesAuditLogger} : verifie la delegation
 * vers {@link RoleGrantAuditRepository} avec resolution du caller UUID
 * + best-effort sur les erreurs ( pas de propagation au caller metier ) .
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("admin.users")
@DisplayName("UserRolesAuditLogger - audit log writer")
class UserRolesAuditLoggerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-11111111aaaa");
    private static final UUID APP_ID  = UUID.fromString("22222222-2222-2222-2222-22222222bbbb");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-33333333cccc");

    @Mock private RoleGrantAuditRepository repository;
    @Mock private AuthenticationService authenticationService;

    private UserRolesAuditLogger logger;

    @BeforeEach
    void setUp() {
        logger = new UserRolesAuditLogger(repository, authenticationService);
    }

    private OreSiUser admin() {
        OreSiUser u = new OreSiUser();
        u.setId(ADMIN_ID);
        u.setLogin("admin");
        return u;
    }

    private CurrentUserRoles asAdmin() {
        return new CurrentUserRoles(List.of("openAdomAdmin"), false, admin());
    }

    @Test
    @DisplayName("logGrant inserts a GRANT row with caller UUID resolved from authenticationService")
    void logGrant_insertsGrantRow() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(asAdmin());

        logger.logGrant(USER_ID, "applicationManager", APP_ID);

        verify(repository).logAction(USER_ID, "applicationManager", APP_ID,
                RoleGrantAudit.ACTION_GRANT, ADMIN_ID);
    }

    @Test
    @DisplayName("logRevoke inserts a REVOKE row with caller UUID")
    void logRevoke_insertsRevokeRow() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(asAdmin());

        logger.logRevoke(USER_ID, "reader", APP_ID);

        verify(repository).logAction(USER_ID, "reader", APP_ID,
                RoleGrantAudit.ACTION_REVOKE, ADMIN_ID);
    }

    @Test
    @DisplayName("null current user -> null grantedBy passed to repository ( no NPE )")
    void logGrant_withNullCurrentUser_passesNullGrantedBy() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(null);

        logger.logGrant(USER_ID, "applicationManager", APP_ID);

        verify(repository).logAction(USER_ID, "applicationManager", APP_ID,
                RoleGrantAudit.ACTION_GRANT, null);
    }

    @Test
    @DisplayName("authenticationService throwing -> logger swallows + passes null grantedBy")
    void logGrant_withAuthServiceException_passesNullGrantedBy() {
        when(authenticationService.getCurrentUserRoles())
                .thenThrow(new RuntimeException("security context absent"));

        assertThatCode(() -> logger.logGrant(USER_ID, "reader", APP_ID))
                .doesNotThrowAnyException();

        verify(repository).logAction(USER_ID, "reader", APP_ID,
                RoleGrantAudit.ACTION_GRANT, null);
    }

    @Test
    @DisplayName("repository throwing -> logger swallows ( best-effort , does not propagate )")
    void logGrant_swallowsRepositoryFailure() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(asAdmin());
        doThrow(new RuntimeException("oa_audit down"))
                .when(repository).logAction(any(), anyString(), any(), anyString(), any());

        assertThatCode(() -> logger.logGrant(USER_ID, "applicationManager", APP_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("global role : applicationId null is forwarded as-is")
    void logGrant_globalRole_passesNullAppId() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(asAdmin());

        logger.logGrant(USER_ID, "openAdomAdmin", null);

        verify(repository).logAction(eq(USER_ID), eq("openAdomAdmin"), eq(null),
                eq(RoleGrantAudit.ACTION_GRANT), eq(ADMIN_ID));
    }
}
