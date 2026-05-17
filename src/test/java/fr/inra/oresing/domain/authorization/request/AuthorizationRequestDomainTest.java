package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs du package domain.authorization.request.
 * Chaque record / sealed interface est couvert en isolation.
 */
@Tag("core.auth")
@DisplayName("AuthorizationRequest domain — records et sealed interface")
class AuthorizationRequestDomainTest {

    // ------------------------------------------------------------------ //
    //  AuthorizationNoRestriction                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationNoRestriction")
    class AuthorizationNoRestrictionTest {

        @Test
        @DisplayName("operationTypes() retourne les types fournis")
        void operationTypes() {
            Set<OperationType> ops = EnumSet.of(OperationType.extraction);
            AuthorizationNoRestriction rec = new AuthorizationNoRestriction(ops);
            assertThat(rec.operationTypes()).isEqualTo(ops);
        }

        @Test
        @DisplayName("timeScope() default retourne null")
        void timeScopeIsNull() {
            AuthorizationNoRestriction rec = new AuthorizationNoRestriction(Set.of());
            assertThat(rec.timeScope()).isNull();
        }

        @Test
        @DisplayName("authorizationScope() default retourne une map vide")
        void authorizationScopeIsEmpty() {
            AuthorizationNoRestriction rec = new AuthorizationNoRestriction(Set.of());
            assertThat(rec.authorizationScope()).isEmpty();
        }

        @Test
        @DisplayName("implémente AuthorizationForScope")
        void implementsInterface() {
            assertThat(new AuthorizationNoRestriction(Set.of()))
                    .isInstanceOf(AuthorizationForScope.class);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationForReferenceScope                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationForReferenceScope")
    class AuthorizationForReferenceScopeTest {

        @Test
        @DisplayName("accesseurs operationTypes et authorizationScope")
        void accessors() {
            Set<OperationType> ops = EnumSet.of(OperationType.depot);
            Map<String, List<fr.inra.oresing.domain.application.configuration.Ltree>> scope = Map.of();
            AuthorizationForReferenceScope rec = new AuthorizationForReferenceScope(ops, scope);
            assertThat(rec.operationTypes()).isEqualTo(ops);
            assertThat(rec.authorizationScope()).isEqualTo(scope);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationForTimeScope                                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationForTimeScope")
    class AuthorizationForTimeScopeTest {

        @Test
        @DisplayName("accesseurs operationTypes et timeScope")
        void accessors() {
            Set<OperationType> ops = EnumSet.of(OperationType.extraction);
            LocalDateTimeRange range = LocalDateTimeRange.always();
            AuthorizationForTimeScope rec = new AuthorizationForTimeScope(ops, range);
            assertThat(rec.operationTypes()).isEqualTo(ops);
            assertThat(rec.timeScope()).isEqualTo(range);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationForReferenceScopeAndTimeScope                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationForReferenceScopeAndTimeScope")
    class AuthorizationForReferenceScopeAndTimeScopeTest {

        @Test
        @DisplayName("accesseurs operationTypes, authorizationScope et timeScope")
        void accessors() {
            Set<OperationType> ops = EnumSet.of(OperationType.depot, OperationType.extraction);
            Map<String, List<fr.inra.oresing.domain.application.configuration.Ltree>> scope = Map.of();
            LocalDateTimeRange range = LocalDateTimeRange.always();
            AuthorizationForReferenceScopeAndTimeScope rec =
                    new AuthorizationForReferenceScopeAndTimeScope(ops, scope, range);
            assertThat(rec.operationTypes()).containsExactlyInAnyOrderElementsOf(ops);
            assertThat(rec.authorizationScope()).isEqualTo(scope);
            assertThat(rec.timeScope()).isEqualTo(range);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationForScope.of()                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationForScope.of()")
    class AuthorizationForScopeOfTest {

        @Test
        @DisplayName("aucune restriction → AuthorizationNoRestriction")
        void noRestriction() {
            AuthorizationInput input = new AuthorizationInput();
            input.setRequiredAuthorizations(null);
            // forcer timeScope à null (le setter attend une Map<String,String>)
            try {
                var f = AuthorizationInput.class.getDeclaredField("timeScope");
                f.setAccessible(true);
                f.set(input, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            input.setOperationTypes(EnumSet.of(OperationType.extraction));
            AuthorizationForScope result = AuthorizationForScope.of(input);
            assertThat(result).isInstanceOf(AuthorizationNoRestriction.class);
        }

        @Test
        @DisplayName("timeScope null mais requiredAuthorizations non vide → AuthorizationForReferenceScope")
        void withReferenceScope() {
            AuthorizationInput input = new AuthorizationInput();
            Map<String, List<fr.inra.oresing.domain.application.configuration.Ltree>> req = new HashMap<>();
            req.put("ref1", List.of());
            input.setRequiredAuthorizations(req);
            // timeScope null
            // override directement le champ via reflection car setTimeScope attend une Map
            try {
                var f = AuthorizationInput.class.getDeclaredField("timeScope");
                f.setAccessible(true);
                f.set(input, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            AuthorizationForScope result = AuthorizationForScope.of(input);
            assertThat(result).isInstanceOf(AuthorizationForReferenceScope.class);
        }

        @Test
        @DisplayName("timeScope 'always' et requiredAuthorizations non vide → AuthorizationForReferenceScope")
        void withReferenceScopeAndAlwaysTimeScope() {
            AuthorizationInput input = new AuthorizationInput();
            Map<String, List<fr.inra.oresing.domain.application.configuration.Ltree>> req = new HashMap<>();
            req.put("ref1", List.of());
            input.setRequiredAuthorizations(req);
            // timeScope = always (valeur par défaut dans AuthorizationInput)
            AuthorizationForScope result = AuthorizationForScope.of(input);
            assertThat(result).isInstanceOf(AuthorizationForReferenceScope.class);
        }

        @Test
        @DisplayName("requiredAuthorizations null et timeScope non null → AuthorizationForTimeScope")
        void withTimeScopeOnly() {
            AuthorizationInput input = new AuthorizationInput();
            input.setRequiredAuthorizations(null);
            // forcer un timeScope différent de always via reflection
            LocalDateTimeRange customRange = LocalDateTimeRange.between(
                    java.time.LocalDateTime.of(2020, 1, 1, 0, 0),
                    java.time.LocalDateTime.of(2024, 12, 31, 0, 0));
            try {
                var f = AuthorizationInput.class.getDeclaredField("timeScope");
                f.setAccessible(true);
                f.set(input, customRange);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            AuthorizationForScope result = AuthorizationForScope.of(input);
            assertThat(result).isInstanceOf(AuthorizationForTimeScope.class);
        }

        @Test
        @DisplayName("requiredAuthorizations non vide et timeScope custom → AuthorizationForReferenceScopeAndTimeScope")
        void withBothScopes() {
            AuthorizationInput input = new AuthorizationInput();
            Map<String, List<fr.inra.oresing.domain.application.configuration.Ltree>> req = new HashMap<>();
            req.put("ref1", List.of());
            input.setRequiredAuthorizations(req);
            LocalDateTimeRange customRange = LocalDateTimeRange.between(
                    java.time.LocalDateTime.of(2020, 1, 1, 0, 0),
                    java.time.LocalDateTime.of(2024, 12, 31, 0, 0));
            try {
                var f = AuthorizationInput.class.getDeclaredField("timeScope");
                f.setAccessible(true);
                f.set(input, customRange);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            AuthorizationForScope result = AuthorizationForScope.of(input);
            assertThat(result).isInstanceOf(AuthorizationForReferenceScopeAndTimeScope.class);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationForAll                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationForAll")
    class AuthorizationForAllTest {

        @Test
        @DisplayName("publication ajoute depot automatiquement")
        void publicationAddsDeploy() {
            Map<String, Set<OperationType>> ops = new HashMap<>();
            ops.put("d1", EnumSet.of(OperationType.publication));
            AuthorizationForAll forAll = new AuthorizationForAll(ops);
            assertThat(forAll.authorizationForAll().get("d1")).contains(OperationType.depot);
        }

        @Test
        @DisplayName("depot ajoute extraction automatiquement")
        void depotAddsExtraction() {
            Map<String, Set<OperationType>> ops = new HashMap<>();
            ops.put("d2", EnumSet.of(OperationType.depot));
            AuthorizationForAll forAll = new AuthorizationForAll(ops);
            assertThat(forAll.authorizationForAll().get("d2")).contains(OperationType.extraction);
        }

        @Test
        @DisplayName("delete ajoute extraction automatiquement")
        void deleteAddsExtraction() {
            Map<String, Set<OperationType>> ops = new HashMap<>();
            ops.put("d3", EnumSet.of(OperationType.delete));
            AuthorizationForAll forAll = new AuthorizationForAll(ops);
            assertThat(forAll.authorizationForAll().get("d3")).contains(OperationType.extraction);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationWithRestriction                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationWithRestriction")
    class AuthorizationWithRestrictionTest {

        @Test
        @DisplayName("publication dans le scope ajoute depot")
        void publicationAddsDeploy() {
            Map<String, AuthorizationForScope> scope = new HashMap<>();
            scope.put("d1", new AuthorizationNoRestriction(EnumSet.of(OperationType.publication)));
            AuthorizationWithRestriction restriction = new AuthorizationWithRestriction(scope);
            assertThat(restriction.authorizationForScope().get("d1").operationTypes())
                    .contains(OperationType.depot);
        }

        @Test
        @DisplayName("extraction seule reste inchangée")
        void extractionStaysAlone() {
            Map<String, AuthorizationForScope> scope = new HashMap<>();
            scope.put("d2", new AuthorizationNoRestriction(EnumSet.of(OperationType.extraction)));
            AuthorizationWithRestriction restriction = new AuthorizationWithRestriction(scope);
            // extraction reste, pas d'ajout de depot
            assertThat(restriction.authorizationForScope().get("d2").operationTypes())
                    .containsExactly(OperationType.extraction);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationRequest                                              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationRequest")
    class AuthorizationRequestTest {

        @Test
        @DisplayName("buildAuthorizationsByDataname — authorizationForAll null retourne map vide")
        void buildWithNullForAll() {
            AuthorizationRequest req = new AuthorizationRequest(
                    UUID.randomUUID(), "name", "desc",
                    UUID.randomUUID(), Set.of(),
                    null, null);
            assertThat(req.buildAuthorizationsByDataname()).isEmpty();
        }

        @Test
        @DisplayName("buildAuthorizationsByDataname — authorizationForAll non null retourne les entrées")
        void buildWithForAll() {
            Map<String, Set<OperationType>> ops = new HashMap<>();
            ops.put("dataset1", EnumSet.of(OperationType.extraction));
            AuthorizationForAll forAll = new AuthorizationForAll(ops);
            AuthorizationRequest req = new AuthorizationRequest(
                    UUID.randomUUID(), "name", "desc",
                    UUID.randomUUID(), Set.of(),
                    forAll, null);
            Map<String, AuthorizationForScope> result = req.buildAuthorizationsByDataname();
            assertThat(result).containsKey("dataset1");
            assertThat(result.get("dataset1")).isInstanceOf(AuthorizationNoRestriction.class);
        }

        @Test
        @DisplayName("buildAuthorizationsByDataname — authorizationWithRestriction fusionne les entrées")
        void buildWithRestriction() {
            Map<String, Set<OperationType>> ops = new HashMap<>();
            ops.put("d1", EnumSet.of(OperationType.extraction));
            AuthorizationForAll forAll = new AuthorizationForAll(ops);

            Map<String, AuthorizationForScope> restrictionScope = new HashMap<>();
            restrictionScope.put("d2", new AuthorizationNoRestriction(EnumSet.of(OperationType.extraction)));
            AuthorizationWithRestriction withRestriction = new AuthorizationWithRestriction(restrictionScope);

            AuthorizationRequest req = new AuthorizationRequest(
                    UUID.randomUUID(), "name", "desc",
                    UUID.randomUUID(), Set.of(),
                    forAll, withRestriction);
            Map<String, AuthorizationForScope> result = req.buildAuthorizationsByDataname();
            assertThat(result).containsKeys("d1", "d2");
        }
    }
}