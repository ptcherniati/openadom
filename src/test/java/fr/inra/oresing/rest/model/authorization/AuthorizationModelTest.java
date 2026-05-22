package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Authorization model – unit tests")
@Tag("domain.model")
class AuthorizationModelTest {

    // ------------------------------------------------------------------
    // AuthorizationInput
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("AuthorizationInput")
    class AuthorizationInputTest {

        @Test
        @DisplayName("Default constructor sets timeScope=always and operationTypes empty")
        void defaultConstructor() {
            AuthorizationInput input = new AuthorizationInput();

            assertThat(input.getTimeScope()).isEqualTo(LocalDateTimeRange.always());
            assertThat(input.getOperationTypes()).isEmpty();
            assertThat(input.getRequiredAuthorizations()).isEmpty();
        }

        @Test
        @DisplayName("Constants FROM_DAY, TO_DAY, FORMAT have expected values")
        void constants() {
            assertThat(AuthorizationInput.FROM_DAY).isEqualTo("fromDay");
            assertThat(AuthorizationInput.TO_DAY).isEqualTo("toDay");
            assertThat(AuthorizationInput.FORMAT).isEqualTo("format");
        }

        @Test
        @DisplayName("Constructor with publication adds depot, delete and extraction automatically")
        void constructorWithPublicationAddsDepotDeleteExtraction() {
            AuthorizationInput input = new AuthorizationInput(
                    Map.of(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.publication)
            );

            assertThat(input.getOperationTypes())
                    .contains(OperationType.publication)
                    .contains(OperationType.depot)
                    .contains(OperationType.delete)
                    .contains(OperationType.extraction);
        }

        @Test
        @DisplayName("Constructor with only extraction keeps just extraction")
        void constructorWithExtractionOnly() {
            AuthorizationInput input = new AuthorizationInput(
                    Map.of(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.extraction)
            );

            assertThat(input.getOperationTypes()).containsExactly(OperationType.extraction);
        }

        @Test
        @DisplayName("Constructor with depot adds extraction automatically")
        void constructorWithDepotAddsExtraction() {
            AuthorizationInput input = new AuthorizationInput(
                    Map.of(),
                    LocalDateTimeRange.always(),
                    EnumSet.of(OperationType.depot)
            );

            assertThat(input.getOperationTypes())
                    .contains(OperationType.depot)
                    .contains(OperationType.extraction);
        }

        @Test
        @DisplayName("setOperationTypes with publication adds depot and extraction via setter")
        void setOperationTypesWithPublication() {
            AuthorizationInput input = new AuthorizationInput();
            Set<OperationType> types = EnumSet.of(OperationType.publication);
            input.setOperationTypes(types);

            assertThat(input.getOperationTypes())
                    .contains(OperationType.publication)
                    .contains(OperationType.depot)
                    .contains(OperationType.extraction);
        }

        @Test
        @DisplayName("setRequiredAuthorizations and getRequiredAuthorizations round-trip")
        void requiredAuthorizationsGetterSetter() {
            AuthorizationInput input = new AuthorizationInput();
            Map<String, List<fr.inra.oresing.domain.application.configuration.Ltree>> auths = new HashMap<>();
            input.setRequiredAuthorizations(auths);

            assertThat(input.getRequiredAuthorizations()).isSameAs(auths);
        }

        @Test
        @DisplayName("Constructor stores timeScope and getTimeScope returns it")
        void timeScopeStoredViaConstructor() {
            LocalDateTimeRange range = LocalDateTimeRange.always();
            AuthorizationInput input = new AuthorizationInput(Map.of(), range, Set.of());

            assertThat(input.getTimeScope()).isEqualTo(range);
        }
    }

    // ------------------------------------------------------------------
    // AuthorizationsResult
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("AuthorizationsResult")
    class AuthorizationsResultTest {

        @Test
        @DisplayName("Compact constructor: applicationCreator=true → isAdministrator=true")
        void isAdministratorWhenCreator() {
            AuthorizationsResult result = new AuthorizationsResult(
                    Map.of(), Map.of(), "myApp",
                    true, false, false, false, false);

            assertThat(result.isAdministrator()).isTrue();
            assertThat(result.applicationCreator()).isTrue();
        }

        @Test
        @DisplayName("Compact constructor: applicationManager=true → isAdministrator=true")
        void isAdministratorWhenManager() {
            AuthorizationsResult result = new AuthorizationsResult(
                    Map.of(), Map.of(), "myApp",
                    false, true, false, false, false);

            assertThat(result.isAdministrator()).isTrue();
            assertThat(result.applicationManager()).isTrue();
        }

        @Test
        @DisplayName("Compact constructor: creator=false, manager=false → isAdministrator=false")
        void notAdministratorWhenNeitherCreatorNorManager() {
            AuthorizationsResult result = new AuthorizationsResult(
                    Map.of(), Map.of(), "myApp",
                    false, false, false, false, false);

            assertThat(result.isAdministrator()).isFalse();
        }

        @Test
        @DisplayName("Canonical constructor passes isAdministrator explicitly")
        void canonicalConstructorExplicitIsAdministrator() {
            AuthorizationsResult result = new AuthorizationsResult(
                    Map.of(), Map.of(), "myApp",
                    false, false, true,
                    false, false, false);

            assertThat(result.isAdministrator()).isTrue();
        }

        @Test
        @DisplayName("Record accessors return correct values")
        void accessors() {
            Map<String, List<AuthorizationParsed>> userAuth = Map.of();
            Map<String, AuthorizationParsed> pubAuth = Map.of();

            AuthorizationsResult result = new AuthorizationsResult(
                    userAuth, pubAuth, "testApp",
                    false, false, true, false, true);

            assertThat(result.userAuthorization()).isSameAs(userAuth);
            assertThat(result.publicAuthorization()).isSameAs(pubAuth);
            assertThat(result.applicationName()).isEqualTo("testApp");
            assertThat(result.userManager()).isTrue();
            assertThat(result.applicationUser()).isFalse();
            assertThat(result.activeApplicationUser()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // CurrentUserRolesResult
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("CurrentUserRolesResult")
    class CurrentUserRolesResultTest {

        @Test
        @DisplayName("of(CurrentUserRoles.EMPTY) maps null user fields correctly")
        void ofEmptyCurrentUserRoles() {
            CurrentUserRoles roles = CurrentUserRoles.EMPTY;

            CurrentUserRolesResult result = CurrentUserRolesResult.of(roles);

            assertThat(result.userId()).isNull();
            assertThat(result.userLogin()).isNull();
            assertThat(result.isOpenAdomAdmin()).isFalse();
            assertThat(result.isApplicationCreator()).isFalse();
            assertThat(result.memberOf()).isEmpty();
            assertThat(result.isDataBaseSuper()).isFalse();
            assertThat(result.applicationRoles()).isEmpty();
        }

        @Test
        @DisplayName("of(CurrentUserRoles) maps user login and id from OreSiUser")
        void ofCurrentUserRolesWithUser() {
            UUID userId = UUID.randomUUID();
            OreSiUser user = new OreSiUser();
            user.setId(userId);
            user.setLogin("alice");

            CurrentUserRoles roles = new CurrentUserRoles(List.of(), false, user);
            CurrentUserRolesResult result = CurrentUserRolesResult.of(roles);

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.userLogin()).isEqualTo("alice");
            assertThat(result.isDataBaseSuper()).isFalse();
        }

        @Test
        @DisplayName("of(CurrentUserRoles) with isDataBaseSuper=true propagates flag")
        void ofCurrentUserRolesDataBaseSuper() {
            CurrentUserRoles roles = new CurrentUserRoles(List.of(), true, null);

            CurrentUserRolesResult result = CurrentUserRolesResult.of(roles);

            assertThat(result.isDataBaseSuper()).isTrue();
        }

        @Test
        @DisplayName("Direct record constructor round-trips all fields")
        void directConstructor() {
            UUID id = UUID.randomUUID();
            Map<String, List<String>> appRoles = Map.of("app1", List.of("reader"));

            CurrentUserRolesResult result = new CurrentUserRolesResult(
                    appRoles, id, "bob", true, false, List.of("role1"), false);

            assertThat(result.applicationRoles()).isEqualTo(appRoles);
            assertThat(result.userId()).isEqualTo(id);
            assertThat(result.userLogin()).isEqualTo("bob");
            assertThat(result.isOpenAdomAdmin()).isTrue();
            assertThat(result.isApplicationCreator()).isFalse();
            assertThat(result.memberOf()).containsExactly("role1");
            assertThat(result.isDataBaseSuper()).isFalse();
        }
    }
}