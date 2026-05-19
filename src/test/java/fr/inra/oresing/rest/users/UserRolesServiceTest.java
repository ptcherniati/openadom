package fr.inra.oresing.rest.users;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserRolesService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>list filtering: login, app, role, state (and combinations);</li>
 *   <li>detail lookup: 404 when user does not exist, scope filtering for non-admins;</li>
 *   <li>grant: delegation to {@link AuthenticationService};</li>
 *   <li>revoke: {@code authorizations} Set is updated and a raw REVOKE is issued
 *       for reader/writer roles;</li>
 *   <li>permission helper {@code isVisibleToCurrentUser} for the manager scope.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("admin.users")
@DisplayName("UserRolesService - admin Users tab")
class UserRolesServiceTest {

    private static final UUID APP_ID_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID APP_ID_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID USER_ALICE = UUID.fromString("11111111-1111-1111-1111-11111111aaaa");
    private static final UUID USER_BOB   = UUID.fromString("22222222-2222-2222-2222-2222222bbbbb");

    @Mock private UserRepository userRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RoleGrantAuditRepository auditRepository;

    private UserRolesService service;

    @BeforeEach
    void setUp() {
        service = new UserRolesService(
                userRepository, applicationRepository, authenticationService,
                jdbcTemplate, auditRepository);
    }

    // ---------------------------------------------------------------- //
    //  fixtures                                                        //
    // ---------------------------------------------------------------- //

    private static OreSiUser user(UUID id, String login, String email,
                                   OreSiUser.OreSiUserStates state) {
        OreSiUser u = new OreSiUser();
        u.setId(id);
        u.setLogin(login);
        u.setEmail(email);
        u.setAccountstate(state);
        return u;
    }

    private static Application app(UUID id, String name) {
        Application a = new Application();
        a.setId(id);
        a.setName(name);
        return a;
    }

    private static CurrentUserRoles roles(OreSiUser user, String... memberOf) {
        return new CurrentUserRoles(List.of(memberOf), false, user);
    }

    private void asAdmin() {
        OreSiUser admin = user(UUID.randomUUID(), "root", "root@example.com",
                OreSiUser.OreSiUserStates.active);
        when(authenticationService.getCurrentUserRoles())
                .thenReturn(roles(admin, "openAdomAdmin"));
    }

    private void asApplicationManager(UUID appId) {
        OreSiUser mgr = user(UUID.randomUUID(), "mgr", "mgr@example.com",
                OreSiUser.OreSiUserStates.active);
        when(authenticationService.getCurrentUserRoles())
                .thenReturn(roles(mgr, appId + "_applicationManager"));
    }

    // ---------------------------------------------------------------- //
    //  list                                                            //
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("list returns all users for an admin caller")
    void listAsAdmin() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.idle);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(alice.getId().toString()))
                .thenReturn(roles(alice, APP_ID_A + "_reader"));
        when(authenticationService.getCurrentUserRoles(bob.getId().toString()))
                .thenReturn(roles(bob, "openAdomAdmin"));

        List<UserDTO.UserSummary> result = service.listUsers(null, null, null, null);

        assertEquals(2, result.size());
        assertEquals("alice", result.get(0).login());
        assertEquals("bob",   result.get(1).login());
    }

    @Test
    @DisplayName("list filters by login substring (case-insensitive)")
    void listFilterByLogin() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.active);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(anyString()))
                .thenReturn(roles(alice));

        List<UserDTO.UserSummary> result = service.listUsers("ALI", null, null, null);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).login());
    }

    @Test
    @DisplayName("list filters by application id")
    void listFilterByApp() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.active);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(alice.getId().toString()))
                .thenReturn(roles(alice, APP_ID_A + "_reader"));
        when(authenticationService.getCurrentUserRoles(bob.getId().toString()))
                .thenReturn(roles(bob, APP_ID_B + "_writer"));

        List<UserDTO.UserSummary> result = service.listUsers(null, APP_ID_A, null, null);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).login());
    }

    @Test
    @DisplayName("list filters by role for the given application")
    void listFilterByRoleScopedToApp() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.active);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(alice.getId().toString()))
                .thenReturn(roles(alice, APP_ID_A + "_reader"));
        when(authenticationService.getCurrentUserRoles(bob.getId().toString()))
                .thenReturn(roles(bob, APP_ID_A + "_writer"));

        List<UserDTO.UserSummary> result = service.listUsers(null, APP_ID_A, "reader", null);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).login());
    }

    @Test
    @DisplayName("list filters by global role openAdomAdmin")
    void listFilterByGlobalRole() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.active);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(alice.getId().toString()))
                .thenReturn(roles(alice, APP_ID_A + "_reader"));
        when(authenticationService.getCurrentUserRoles(bob.getId().toString()))
                .thenReturn(roles(bob, "openAdomAdmin"));

        List<UserDTO.UserSummary> result = service.listUsers(null, null, "openAdomAdmin", null);

        assertEquals(1, result.size());
        assertEquals("bob", result.get(0).login());
    }

    @Test
    @DisplayName("list filters by account state")
    void listFilterByState() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.closed);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(anyString()))
                .thenReturn(roles(alice));

        List<UserDTO.UserSummary> result = service.listUsers(null, null, null, "closed");

        assertEquals(1, result.size());
        assertEquals("bob", result.get(0).login());
    }

    @Test
    @DisplayName("list with 'all' state filter keeps every state")
    void listFilterAllState() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.closed);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        when(authenticationService.getCurrentUserRoles(anyString()))
                .thenReturn(roles(alice));

        List<UserDTO.UserSummary> result = service.listUsers(null, null, null, "all");
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("list refuses callers that are neither admin nor manager")
    void listForbiddenForRegularUser() {
        OreSiUser regular = user(UUID.randomUUID(), "joe", "joe@x",
                OreSiUser.OreSiUserStates.active);
        when(authenticationService.getCurrentUserRoles())
                .thenReturn(roles(regular, "someUnrelatedRole"));

        assertThrows(AccessDeniedException.class,
                () -> service.listUsers(null, null, null, null));
    }

    @Test
    @DisplayName("list as applicationManager hides users that have no role on a managed app")
    void listScopedToManagerApps() {
        asApplicationManager(APP_ID_A);
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        OreSiUser bob   = user(USER_BOB,   "bob",   "bob@x",   OreSiUser.OreSiUserStates.active);
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));
        // Alice has a role on APP_A : visible. Bob only on APP_B : hidden.
        when(authenticationService.getCurrentUserRoles(alice.getId().toString()))
                .thenReturn(roles(alice, APP_ID_A + "_reader"));
        when(authenticationService.getCurrentUserRoles(bob.getId().toString()))
                .thenReturn(roles(bob, APP_ID_B + "_writer"));

        List<UserDTO.UserSummary> result = service.listUsers(null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).login());
    }

    // ---------------------------------------------------------------- //
    //  detail                                                          //
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("findDetail returns empty when the user does not exist")
    void detailNotFound() {
        asAdmin();
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.empty());

        Optional<UserDTO.UserDetail> result = service.findDetail(USER_ALICE);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findDetail returns a non-empty payload for an admin caller")
    void detailVisibleToAdmin() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        when(authenticationService.getCurrentUserRoles(USER_ALICE.toString()))
                .thenReturn(roles(alice, APP_ID_A + "_reader", "openAdomAdmin"));
        when(applicationRepository.tryFindApplication(APP_ID_A.toString()))
                .thenReturn(Optional.of(app(APP_ID_A, "appA")));

        Optional<UserDTO.UserDetail> result = service.findDetail(USER_ALICE);

        assertTrue(result.isPresent());
        UserDTO.UserDetail detail = result.get();
        assertEquals("alice", detail.login());
        assertEquals(1, detail.applications().size());
        assertEquals("appA", detail.applications().get(0).applicationName());
        assertTrue(detail.globalRoles().stream()
                .anyMatch(r -> "openAdomAdmin".equals(r.roleName())
                        && UserDTO.SCOPE_GLOBAL.equals(r.scope())));
        // Application reader attribution carries the SQL identifier .
        assertTrue(detail.applications().get(0).roles().stream()
                .anyMatch(r -> "reader".equals(r.roleName())
                        && r.sqlRoleName().startsWith(APP_ID_A.toString())));
    }

    @Test
    @DisplayName("findDetail hides users outside the manager's scope")
    void detailScopedHidden() {
        asApplicationManager(APP_ID_A);
        OreSiUser bob = user(USER_BOB, "bob", "bob@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_BOB)).thenReturn(Optional.of(bob));
        when(authenticationService.getCurrentUserRoles(USER_BOB.toString()))
                .thenReturn(roles(bob, APP_ID_B + "_reader"));

        Optional<UserDTO.UserDetail> result = service.findDetail(USER_BOB);
        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------- //
    //  grant                                                           //
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("grant openAdomAdmin delegates to AuthenticationService")
    void grantOpenAdomAdminDelegates() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));

        service.grantRole(USER_ALICE, null, "openAdomAdmin");

        verify(authenticationService).addUserRightopenAdomAdmin(USER_ALICE);
    }

    @Test
    @DisplayName("grant applicationManager delegates with the looked-up Application")
    void grantApplicationManagerDelegates() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        Application appA = app(APP_ID_A, "appA");
        when(applicationRepository.findApplication(APP_ID_A)).thenReturn(appA);

        service.grantRole(USER_ALICE, APP_ID_A, "applicationManager");

        verify(authenticationService).addUserRightApplicationManager(USER_ALICE, appA);
    }

    @Test
    @DisplayName("grant userManager (app-scoped) delegates")
    void grantUserManagerDelegates() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        Application appA = app(APP_ID_A, "appA");
        when(applicationRepository.findApplication(APP_ID_A)).thenReturn(appA);

        service.grantRole(USER_ALICE, APP_ID_A, "userManager");

        verify(authenticationService).addUserRightUserManager(USER_ALICE, appA);
    }

    @Test
    @DisplayName("grant reader uses raw GRANT and updates authorizations Set")
    void grantReaderRawSql() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        alice.setAuthorizations(new HashSet<>());
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        when(userRepository.findById(USER_ALICE)).thenReturn(alice);
        when(applicationRepository.findApplication(APP_ID_A)).thenReturn(app(APP_ID_A, "appA"));

        service.grantRole(USER_ALICE, APP_ID_A, "reader");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertTrue(sql.getValue().startsWith("GRANT \""));
        assertTrue(sql.getValue().contains(APP_ID_A.toString()));
        assertTrue(sql.getValue().contains("_reader"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> setCaptor = ArgumentCaptor.forClass(Set.class);
        verify(userRepository).updateAuthorizations(eq(USER_ALICE), setCaptor.capture());
        assertTrue(setCaptor.getValue().stream()
                .anyMatch(s -> s.endsWith("_reader") && s.startsWith(APP_ID_A.toString())));
    }

    @Test
    @DisplayName("grant fails for unknown application role")
    void grantUnknownRole() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));

        assertThrows(IllegalArgumentException.class,
                () -> service.grantRole(USER_ALICE, APP_ID_A, "ghost"));
    }

    @Test
    @DisplayName("grant fails for unknown user")
    void grantUnknownUser() {
        asAdmin();
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.grantRole(USER_ALICE, APP_ID_A, "reader"));
    }

    @Test
    @DisplayName("global role grant denied for non-admin manager")
    void grantGlobalRoleDeniedForManager() {
        asApplicationManager(APP_ID_A);
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(applicationRepository.tryFindApplication(APP_ID_A.toString()))
                .thenReturn(Optional.of(app(APP_ID_A, "appA")));
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));

        assertThrows(AccessDeniedException.class,
                () -> service.grantRole(USER_ALICE, null, "openAdomAdmin"));
    }

    @Test
    @DisplayName("app role grant by manager allowed only on her managed application")
    void grantAppRoleByForeignManagerDenied() {
        asApplicationManager(APP_ID_A);
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        Application appB = app(APP_ID_B, "appB");
        when(applicationRepository.tryFindApplication(APP_ID_B.toString()))
                .thenReturn(Optional.of(appB));

        assertThrows(AccessDeniedException.class,
                () -> service.grantRole(USER_ALICE, APP_ID_B, "reader"));
        verify(jdbcTemplate, never()).execute(anyString());
    }

    // ---------------------------------------------------------------- //
    //  revoke                                                          //
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("revoke applicationManager delegates to AuthenticationService")
    void revokeApplicationManagerDelegates() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        Application appA = app(APP_ID_A, "appA");
        when(applicationRepository.findApplication(APP_ID_A)).thenReturn(appA);

        service.revokeRole(USER_ALICE, APP_ID_A, "applicationManager");

        verify(authenticationService).deleteUserRightApplicationManager(USER_ALICE, appA);
    }

    @Test
    @DisplayName("revoke reader issues a raw REVOKE and removes the role from authorizations")
    void revokeReaderRawSql() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        Set<String> existing = new HashSet<>();
        existing.add(APP_ID_A + "_reader");
        existing.add("someOtherAuth");
        alice.setAuthorizations(existing);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        when(userRepository.findById(USER_ALICE)).thenReturn(alice);
        when(applicationRepository.findApplication(APP_ID_A))
                .thenReturn(app(APP_ID_A, "appA"));

        service.revokeRole(USER_ALICE, APP_ID_A, "reader");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertTrue(sql.getValue().startsWith("REVOKE \""));
        assertTrue(sql.getValue().contains("_reader"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> setCaptor = ArgumentCaptor.forClass(Set.class);
        verify(userRepository).updateAuthorizations(eq(USER_ALICE), setCaptor.capture());
        // The reader role is gone, but unrelated authorizations are preserved.
        assertFalse(setCaptor.getValue().contains(APP_ID_A + "_reader"));
        assertTrue(setCaptor.getValue().contains("someOtherAuth"));
    }

    @Test
    @DisplayName("revoke fails for an unknown role")
    void revokeUnknownRole() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));

        assertThrows(IllegalArgumentException.class,
                () -> service.revokeRole(USER_ALICE, APP_ID_A, "ghost"));
    }

    // ---------------------------------------------------------------- //
    //  isVisibleToCurrentUser                                          //
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("isVisibleToCurrentUser : admin sees everyone")
    void visibleToAdmin() {
        OreSiUser admin = user(UUID.randomUUID(), "root", "root@x",
                OreSiUser.OreSiUserStates.active);
        CurrentUserRoles adminRoles = roles(admin, "openAdomAdmin");
        OreSiUser candidate = user(USER_ALICE, "alice", "alice@x",
                OreSiUser.OreSiUserStates.active);
        assertTrue(service.isVisibleToCurrentUser(candidate, adminRoles));
    }

    @Test
    @DisplayName("isVisibleToCurrentUser : manager sees only users tied to her apps")
    void visibleToManager() {
        OreSiUser mgr = user(UUID.randomUUID(), "mgr", "mgr@x",
                OreSiUser.OreSiUserStates.active);
        CurrentUserRoles mgrRoles = roles(mgr, APP_ID_A + "_applicationManager");
        OreSiUser candidate = user(USER_ALICE, "alice", "alice@x",
                OreSiUser.OreSiUserStates.active);

        // Same app : visible.
        when(authenticationService.getCurrentUserRoles(USER_ALICE.toString()))
                .thenReturn(roles(candidate, APP_ID_A + "_reader"));
        assertTrue(service.isVisibleToCurrentUser(candidate, mgrRoles));

        // Different app : hidden.
        when(authenticationService.getCurrentUserRoles(USER_ALICE.toString()))
                .thenReturn(roles(candidate, APP_ID_B + "_reader"));
        assertFalse(service.isVisibleToCurrentUser(candidate, mgrRoles));
    }

    @Test
    @DisplayName("buildAppRoleSqlName truncates to PG identifier limit (63)")
    void buildAppRoleSqlNameWithinPgLimit() {
        String name = UserRolesService.buildAppRoleSqlName(APP_ID_A, "applicationManager");
        assertNotNull(name);
        assertTrue(name.length() <= 63);
        assertTrue(name.startsWith(APP_ID_A.toString()));
    }

    @Test
    @DisplayName("appRolePattern matches the expected role conventions")
    void appRolePatternMatches() {
        assertTrue(UserRolesService.appRolePattern()
                .matcher(APP_ID_A + "_applicationManager").matches());
        assertTrue(UserRolesService.appRolePattern()
                .matcher(APP_ID_A + "_reader").matches());
        assertFalse(UserRolesService.appRolePattern()
                .matcher("openAdomAdmin").matches());
    }

    @Test
    @DisplayName("revoke called with an unknown user throws IllegalArgumentException")
    void revokeUnknownUser() {
        asAdmin();
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.revokeRole(USER_ALICE, APP_ID_A, "reader"));
        verify(jdbcTemplate, never()).execute(anyString());
        verify(authenticationService, never()).deleteUserRightApplicationManager(any(), any());
    }

    // ---------------------------------------------------------------- //
    //  audit logging                                                   //
    // ---------------------------------------------------------------- //

    @Test
    @DisplayName("grantRole appends a GRANT row in oa_audit.role_grant_audit")
    void grantLogsAuditEntry() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        when(applicationRepository.findApplication(APP_ID_A))
                .thenReturn(app(APP_ID_A, "appA"));

        service.grantRole(USER_ALICE, APP_ID_A, "applicationManager");

        verify(auditRepository).logAction(
                eq(USER_ALICE), eq("applicationManager"), eq(APP_ID_A),
                eq(RoleGrantAudit.ACTION_GRANT), any());
    }

    @Test
    @DisplayName("revokeRole appends a REVOKE row in oa_audit.role_grant_audit")
    void revokeLogsAuditEntry() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        when(applicationRepository.findApplication(APP_ID_A))
                .thenReturn(app(APP_ID_A, "appA"));

        service.revokeRole(USER_ALICE, APP_ID_A, "applicationManager");

        verify(auditRepository).logAction(
                eq(USER_ALICE), eq("applicationManager"), eq(APP_ID_A),
                eq(RoleGrantAudit.ACTION_REVOKE), any());
    }

    @Test
    @DisplayName("audit failure does not block the underlying grant operation")
    void grantContinuesOnAuditFailure() {
        asAdmin();
        OreSiUser alice = user(USER_ALICE, "alice", "alice@x", OreSiUser.OreSiUserStates.active);
        when(userRepository.tryFindById(USER_ALICE)).thenReturn(Optional.of(alice));
        when(applicationRepository.findApplication(APP_ID_A))
                .thenReturn(app(APP_ID_A, "appA"));
        org.mockito.Mockito.doThrow(new RuntimeException("oa_audit down"))
                .when(auditRepository).logAction(any(), anyString(), any(), anyString(), any());

        // No exception thrown to the caller .
        service.grantRole(USER_ALICE, APP_ID_A, "applicationManager");
        verify(authenticationService).addUserRightApplicationManager(eq(USER_ALICE), any());
    }
}
