package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalRoleToBeGranted;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires des records simples du package role (NotConnectedUser et variantes)
 * ainsi que des enums PrivilegeApplicationDomainEnum / PrivilegeSystemDomainEnum
 * et des méthodes de OpenAdomAdmin.
 */
@Tag("core.auth")
@DisplayName("Role domain — records simples, enums et OpenAdomAdmin")
class NotConnectedRoleDomainTest {

    // --- PrivilegeApplicationDomainEnum --------------------------------- //

    @Nested
    @DisplayName("PrivilegeApplicationDomainEnum")
    class PrivilegeApplicationDomainEnumTest {

        @Test
        @DisplayName("toutes les valeurs sont accessibles via values()")
        void allValuesAccessible() {
            PrivilegeApplicationDomainEnum[] values = PrivilegeApplicationDomainEnum.values();
            assertThat(values).hasSizeGreaterThanOrEqualTo(1);
            assertThat(PrivilegeApplicationDomainEnum.valueOf("APPLICATION_MANAGER"))
                    .isEqualTo(PrivilegeApplicationDomainEnum.APPLICATION_MANAGER);
            assertThat(PrivilegeApplicationDomainEnum.valueOf("DATA_MANAGEMENT"))
                    .isEqualTo(PrivilegeApplicationDomainEnum.DATA_MANAGEMENT);
            assertThat(PrivilegeApplicationDomainEnum.valueOf("DATA_READ"))
                    .isEqualTo(PrivilegeApplicationDomainEnum.DATA_READ);
            assertThat(PrivilegeApplicationDomainEnum.valueOf("DATA_WRITE"))
                    .isEqualTo(PrivilegeApplicationDomainEnum.DATA_WRITE);
        }
    }

    // --- PrivilegeSystemDomainEnum -------------------------------------- //

    @Nested
    @DisplayName("PrivilegeSystemDomainEnum")
    class PrivilegeSystemDomainEnumTest {

        @Test
        @DisplayName("toutes les valeurs sont accessibles via values()")
        void allValuesAccessible() {
            PrivilegeSystemDomainEnum[] values = PrivilegeSystemDomainEnum.values();
            assertThat(values).hasSizeGreaterThanOrEqualTo(1);
            assertThat(PrivilegeSystemDomainEnum.valueOf("SYSTEM_USER_CONNECTED"))
                    .isEqualTo(PrivilegeSystemDomainEnum.SYSTEM_USER_CONNECTED);
            assertThat(PrivilegeSystemDomainEnum.valueOf("SYSTEM_ADMINISTRATION"))
                    .isEqualTo(PrivilegeSystemDomainEnum.SYSTEM_ADMINISTRATION);
        }
    }

    // --- ConnectedUser -------------------------------------------------- //

    @Nested
    @DisplayName("ConnectedUser")
    class ConnectedUserTest {

        @Test
        @DisplayName("getLogin() délègue à CurrentUserRoles.userLogin()")
        void getLoginDelegatesToRoles() {
            OreSiUser user = new OreSiUser();
            user.setLogin("alice");
            CurrentUserRoles roles = new CurrentUserRoles(List.of(), false, user);
            ConnectedUser connectedUser = new ConnectedUser(roles, Set.of("app1"));
            assertThat(connectedUser.getLogin()).isEqualTo("alice");
        }

        @Test
        @DisplayName("applicationCreator() retourne le bon ensemble")
        void applicationCreatorField() {
            CurrentUserRoles roles = CurrentUserRoles.EMPTY_INSTANCE;
            Set<String> creators = Set.of("myApp");
            ConnectedUser connectedUser = new ConnectedUser(roles, creators);
            assertThat(connectedUser.applicationCreator()).isEqualTo(creators);
        }
    }

    // --- NotConnectedAuthentifiedActiveUser ----------------------------- //

    @Nested
    @DisplayName("NotConnectedAuthentifiedActiveUser")
    class NotConnectedAuthentifiedActiveUserTest {

        @Test
        @DisplayName("accesseurs user et createUserRequest")
        void accessors() {
            OreSiUser user = new OreSiUser();
            user.setLogin("bob");
            CreateUserRequest req = new CreateUserRequest();
            NotConnectedAuthentifiedActiveUser record =
                    new NotConnectedAuthentifiedActiveUser(user, req);
            assertThat(record.user()).isEqualTo(user);
            assertThat(record.createUserRequest()).isEqualTo(req);
        }
    }

    // --- NotConnectedAuthentifiedActiveUserNotSignedCharte -------------- //

    @Nested
    @DisplayName("NotConnectedAuthentifiedActiveUserNotSignedCharte")
    class NotConnectedAuthentifiedActiveUserNotSignedCharteTest {

        @Test
        @DisplayName("accesseurs user, createUserRequest et charte")
        void accessors() {
            OreSiUser user = new OreSiUser();
            CreateUserRequest req = new CreateUserRequest();
            NotConnectedAuthentifiedActiveUserNotSignedCharte record =
                    new NotConnectedAuthentifiedActiveUserNotSignedCharte(user, req, "GDU-2024");
            assertThat(record.user()).isEqualTo(user);
            assertThat(record.createUserRequest()).isEqualTo(req);
            assertThat(record.charte()).isEqualTo("GDU-2024");
        }
    }

    // --- NotConnectedAuthentifiedIdleUser ------------------------------- //

    @Nested
    @DisplayName("NotConnectedAuthentifiedIdleUser")
    class NotConnectedAuthentifiedIdleUserTest {

        @Test
        @DisplayName("accesseurs user et createUserRequest")
        void accessors() {
            OreSiUser user = new OreSiUser();
            CreateUserRequest req = new CreateUserRequest();
            NotConnectedAuthentifiedIdleUser record =
                    new NotConnectedAuthentifiedIdleUser(user, req);
            assertThat(record.user()).isEqualTo(user);
            assertThat(record.createUserRequest()).isEqualTo(req);
        }
    }

    // --- NotConnectedAuthentifiedMissingPasswordUser -------------------- //

    @Nested
    @DisplayName("NotConnectedAuthentifiedMissingPasswordUser")
    class NotConnectedAuthentifiedMissingPasswordUserTest {

        @Test
        @DisplayName("accesseurs oreSiUser et createUserRequest")
        void accessors() {
            OreSiUser user = new OreSiUser();
            CreateUserRequest req = new CreateUserRequest();
            NotConnectedAuthentifiedMissingPasswordUser record =
                    new NotConnectedAuthentifiedMissingPasswordUser(user, req);
            assertThat(record.oreSiUser()).isEqualTo(user);
            assertThat(record.createUserRequest()).isEqualTo(req);
        }
    }

    // --- NotConnectedAuthentifiedPendingUser ---------------------------- //

    @Nested
    @DisplayName("NotConnectedAuthentifiedPendingUser")
    class NotConnectedAuthentifiedPendingUserTest {

        @Test
        @DisplayName("accesseur user")
        void accessors() {
            OreSiUser user = new OreSiUser();
            user.setLogin("pending");
            NotConnectedAuthentifiedPendingUser record =
                    new NotConnectedAuthentifiedPendingUser(user);
            assertThat(record.user()).isEqualTo(user);
        }
    }

    // --- NotConnectedAuthentifiedClosedUser ----------------------------- //

    @Nested
    @DisplayName("NotConnectedAuthentifiedClosedUser")
    class NotConnectedAuthentifiedClosedUserTest {

        @Test
        @DisplayName("accesseur loginAdminResult")
        void accessors() {
            LoginAdminResult result = new LoginAdminResult(
                    null, "closedUser", null, "CLOSED",
                    false, false, Set.of(), java.util.Map.of(), null);
            NotConnectedAuthentifiedClosedUser record =
                    new NotConnectedAuthentifiedClosedUser(result);
            assertThat(record.loginAdminResult()).isEqualTo(result);
        }
    }

    // --- NotConnectedUnauthentifiedUser --------------------------------- //

    @Nested
    @DisplayName("NotConnectedUnauthentifiedUser")
    class NotConnectedUnauthentifiedUserTest {

        @Test
        @DisplayName("accesseur createUserRequest")
        void accessors() {
            CreateUserRequest req = new CreateUserRequest();
            NotConnectedUnauthentifiedUser record = new NotConnectedUnauthentifiedUser(req);
            assertThat(record.createUserRequest()).isEqualTo(req);
        }
    }

    // --- NotConnectedUnauthentifiedUserForCreate ------------------------ //

    @Nested
    @DisplayName("NotConnectedUnauthentifiedUserForCreate")
    class NotConnectedUnauthentifiedUserForCreateTest {

        @Test
        @DisplayName("getPassword() retourne null")
        void passwordIsNull() {
            NotConnectedUnauthentifiedUserForCreate record = new NotConnectedUnauthentifiedUserForCreate();
            assertThat(record.getPassword()).isNull();
        }

        @Test
        @DisplayName("getUsername() retourne 'anonymous'")
        void usernameIsAnonymous() {
            NotConnectedUnauthentifiedUserForCreate record = new NotConnectedUnauthentifiedUserForCreate();
            assertThat(record.getUsername()).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("getAuthorities() non vide (ROLE_UNAUTHENTIFIED_UPDATE_USER)")
        void authoritiesNotEmpty() {
            NotConnectedUnauthentifiedUserForCreate record = new NotConnectedUnauthentifiedUserForCreate();
            assertThat(record.getAuthorities()).isNotEmpty();
        }

        @Test
        @DisplayName("flags de compte tous à true")
        void allAccountFlagsTrue() {
            NotConnectedUnauthentifiedUserForCreate record = new NotConnectedUnauthentifiedUserForCreate();
            assertThat(record.isAccountNonExpired()).isTrue();
            assertThat(record.isAccountNonLocked()).isTrue();
            assertThat(record.isCredentialsNonExpired()).isTrue();
            assertThat(record.isEnabled()).isTrue();
        }
    }

    // --- OpenAdomAdmin -------------------------------------------------- //

    @Nested
    @DisplayName("OpenAdomAdmin")
    class OpenAdomAdminTest {

        @Test
        @DisplayName("canCreateApplication() retourne true pour n'importe quel nom")
        void canCreateApplicationAlwaysTrue() {
            OpenAdomAdmin admin = new OpenAdomAdmin();
            assertThat(admin.canCreateApplication("anyApp")).isTrue();
        }

        @Test
        @DisplayName("OPEN_ADOM_ADMIN_ROLE est 'openAdomAdmin'")
        void constantValue() {
            assertThat(OpenAdomAdmin.OPEN_ADOM_ADMIN_ROLE).isEqualTo("openAdomAdmin");
        }

        @Test
        @DisplayName("canManagerRightForRole — rôle valide openAdomAdmin ne lève pas d'exception")
        void canManagerRightForRoleValidOpenAdomAdmin() {
            OpenAdomAdmin admin = new OpenAdomAdmin();
            OreSiRoleForUser role = new OreSiRoleForUser("uid", "openAdomAdmin", null);
            assertThatNoException().isThrownBy(() -> admin.canManagerRightForRole(role));
        }

        @Test
        @DisplayName("canManagerRightForRole — rôle valide applicationCreator ne lève pas d'exception")
        void canManagerRightForRoleValidApplicationCreator() {
            OpenAdomAdmin admin = new OpenAdomAdmin();
            OreSiRoleForUser role = new OreSiRoleForUser("uid", ApplicationCreatorUser.APPLICATION_CREATOR_ROLE, null);
            assertThatNoException().isThrownBy(() -> admin.canManagerRightForRole(role));
        }

        @Test
        @DisplayName("canManagerRightForRole — rôle inconnu lève IllegalRoleToBeGranted")
        void canManagerRightForRoleInvalidThrows() {
            OpenAdomAdmin admin = new OpenAdomAdmin();
            OreSiRoleForUser role = new OreSiRoleForUser("uid", "unknownRole", null);
            assertThatThrownBy(() -> admin.canManagerRightForRole(role))
                    .isInstanceOf(IllegalRoleToBeGranted.class);
        }
    }
}