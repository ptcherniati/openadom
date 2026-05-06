package fr.inra.oresing.rest;

import fr.inra.oresing.domain.PolicyDescription;
import fr.inra.oresing.domain.additionalfiles.OreSiAdditionalFileAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour {@link UpdateRolesOnAdditionalFilesManagement}.
 * Tous les collaborateurs infrastructure sont mockés (pas de Spring context).
 */
@Tag("core.auth")
class UpdateRolesOnAdditionalFilesManagementTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------
    private SqlService db;
    private AuthenticationService authenticationService;
    private OreSiRepository repository;
    private OreSiRepository.RepositoryForApplication repoForApp;
    private AuthorizationAdditionalFilesRepository authFilesRepo;
    private ApplicationRepository appRepo;
    private Application application;

    private UpdateRolesOnAdditionalFilesManagement sut;

    private static final UUID APP_ID = UUID.randomUUID();
    private static final UUID AUTH_ID = UUID.randomUUID();
    private static final UUID USER1 = UUID.randomUUID();
    private static final UUID USER2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        db = Mockito.mock(SqlService.class);
        authenticationService = Mockito.mock(AuthenticationService.class);
        repository = Mockito.mock(OreSiRepository.class);
        appRepo = Mockito.mock(ApplicationRepository.class);
        repoForApp = Mockito.mock(OreSiRepository.RepositoryForApplication.class);
        authFilesRepo = Mockito.mock(AuthorizationAdditionalFilesRepository.class);

        application = Mockito.mock(Application.class);
        when(application.getId()).thenReturn(APP_ID);
        when(application.getName()).thenReturn("test-app");

        // Chaîne repository
        when(repository.application()).thenReturn(appRepo);
        when(appRepo.findApplication(APP_ID)).thenReturn(application);
        when(repository.getRepository(application)).thenReturn(repoForApp);
        when(repoForApp.authorizationAdditionalFiles()).thenReturn(authFilesRepo);

        sut = new UpdateRolesOnAdditionalFilesManagement(repository, db, authenticationService);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OreSiAdditionalFileAuthorization makeAuthorization(UUID authId, UUID appId,
                                                                Set<UUID> users,
                                                                Map<OperationAdditionalFileType, List<String>> ops) {
        OreSiAdditionalFileAuthorization auth = new OreSiAdditionalFileAuthorization();
        auth.setId(authId);
        auth.setApplication(appId);
        auth.setOreSiUsers(users);
        auth.setAdditionalFiles(ops);
        return auth;
    }

    private OreSiUserRole mockUserRole() {
        return Mockito.mock(OreSiUserRole.class);
    }

    private void stubUserRoleMock(UUID... userIds) {
        for (UUID userId : userIds) {
            when(authenticationService.getUserRole(userId)).thenReturn(mockUserRole());
        }
    }

    // -------------------------------------------------------------------------
    // dropPolicies
    // -------------------------------------------------------------------------
    @Nested
    class DropPolicies {

        @Test
        void dropPoliciesCallsDbForEachPolicy() {
            PolicyDescription pd = new PolicyDescription();
            pd.setPolicyname("pol1");
            pd.setTablename("additionalBinaryFile");
            when(db.getPoliciesForRole(any())).thenReturn(List.of(pd));

            OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.managementRole(application, AUTH_ID);
            sut.dropPolicies(role);

            verify(db).getPoliciesForRole(role);
            verify(db, times(1)).dropPolicy(any());
        }

        @Test
        void dropPoliciesWithNoExistingPoliciesDoesNothing() {
            when(db.getPoliciesForRole(any())).thenReturn(List.of());
            OreSiRightOnApplicationRole role = OreSiRightOnApplicationRole.managementRole(application, AUTH_ID);
            sut.dropPolicies(role);
            verify(db, never()).dropPolicy(any());
        }
    }

    // -------------------------------------------------------------------------
    // updateRoleForManagement — avec opération admin
    // -------------------------------------------------------------------------
    @Nested
    class UpdateRoleForManagementAdmin {

        @BeforeEach
        void initSut() {
            when(db.getPoliciesForRole(any())).thenReturn(List.of());
            stubUserRoleMock(USER1, USER2);

            OreSiAdditionalFileAuthorization auth = makeAuthorization(
                    AUTH_ID, APP_ID, Set.of(USER1, USER2),
                    Map.of(OperationAdditionalFileType.admin, List.of("file1")));

            sut.init(Set.of(), auth); // aucun utilisateur précédent
        }

        @Test
        void adminOperationCreatesAllStatementsPolicies() {
            sut.updateRoleForManagement();
            // Pour admin → 2 createPolicy calls : additionalFilePolicy + binaryFilePolicy
            verify(db, atLeastOnce()).createPolicy(any(SqlPolicy.class));
        }

        @Test
        void newUsersAreAddedToRole() {
            sut.updateRoleForManagement();
            // USER1 et USER2 sont nouveaux (previousUsers vide) → addUserInRole x2
            verify(authenticationService, atLeastOnce()).getUserRole(any(UUID.class));
            verify(db, atLeastOnce()).addUserInRole(any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // updateRoleForManagement — avec opération delete
    // -------------------------------------------------------------------------
    @Test
    void deleteOperationCreatesDeletePolicies() {
        when(db.getPoliciesForRole(any())).thenReturn(List.of());
        stubUserRoleMock(USER1);

        OreSiAdditionalFileAuthorization auth = makeAuthorization(
                AUTH_ID, APP_ID, Set.of(USER1),
                Map.of(OperationAdditionalFileType.delete, List.of("filetype1")));

        sut.init(Set.of(), auth);
        sut.updateRoleForManagement();

        verify(db, atLeastOnce()).createPolicy(any(SqlPolicy.class));
    }

    // -------------------------------------------------------------------------
    // updateRoleForManagement — avec opération depot
    // -------------------------------------------------------------------------
    @Test
    void depotOperationCreatesInsertPolicies() {
        when(db.getPoliciesForRole(any())).thenReturn(List.of());
        stubUserRoleMock(USER1);

        OreSiAdditionalFileAuthorization auth = makeAuthorization(
                AUTH_ID, APP_ID, Set.of(USER1),
                Map.of(OperationAdditionalFileType.depot, List.of("filetype1")));

        sut.init(Set.of(), auth);
        sut.updateRoleForManagement();

        verify(db, atLeastOnce()).createPolicy(any(SqlPolicy.class));
    }

    // -------------------------------------------------------------------------
    // updateRoleForManagement — comportement du diff utilisateurs
    // -------------------------------------------------------------------------
    @Test
    void sharedUsersAreRemovedAndReaddedDueToCurrentDiffLogic() {
        // NOTE: UpdateRolesOnAdditionalFilesManagement utilise Sets.difference(previous, new)
        // au lieu de Sets.intersection. Les utilisateurs présents dans les DEUX sets (inchangés)
        // declenchent un removeUserInRole + addUserInRole (comportement actuel).
        // Les utilisateurs retirés (dans previous seulement) ne declenchent PAS removeUserInRole.
        when(db.getPoliciesForRole(any())).thenReturn(List.of());
        stubUserRoleMock(USER1, USER2);

        Set<UUID> previousUsers = Set.of(USER1, USER2);  // les deux étaient là
        // L'autorisation ne contient plus que USER1 → USER2 "retiré", USER1 "inchangé"
        OreSiAdditionalFileAuthorization auth = makeAuthorization(
                AUTH_ID, APP_ID, Set.of(USER1),  // USER1 reste, USER2 part
                Map.of(OperationAdditionalFileType.admin, List.of("fil")));

        sut.init(previousUsers, auth);
        sut.updateRoleForManagement();

        // Avec la logique actuelle, USER1 (dans les deux sets) déclenche removeUserInRole
        verify(db, atLeastOnce()).removeUserInRole(any(), any());
        // USER1 est aussi ajouté (dans newUsers)
        verify(db, atLeastOnce()).addUserInRole(any(), any());
    }

    // -------------------------------------------------------------------------
    // createExpression — liste vide → expression vide (null ou "")
    // -------------------------------------------------------------------------
    @Test
    void emptyFileListResultsInEmptyExpression() {
        when(db.getPoliciesForRole(any())).thenReturn(List.of());
        stubUserRoleMock(USER1);

        OreSiAdditionalFileAuthorization auth = makeAuthorization(
                AUTH_ID, APP_ID, Set.of(USER1),
                Map.of(OperationAdditionalFileType.admin, List.of()));

        sut.init(Set.of(), auth);
        sut.updateRoleForManagement();

        ArgumentCaptor<SqlPolicy> captor = ArgumentCaptor.forClass(SqlPolicy.class);
        verify(db, atLeastOnce()).createPolicy(captor.capture());
        boolean anyNullOrEmptyExpression = captor.getAllValues().stream()
                .anyMatch(p -> p.usingExpression() == null || p.usingExpression().isEmpty());
        assertThat(anyNullOrEmptyExpression).isTrue();
    }
}