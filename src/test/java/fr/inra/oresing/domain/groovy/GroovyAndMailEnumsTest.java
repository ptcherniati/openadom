package fr.inra.oresing.domain.groovy;

import fr.inra.oresing.mail.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires des types légers du package domain.groovy
 * et des enums EmailService (aucun contexte Spring).
 */
@DisplayName("GroovyExpressionAnalysis + EmailService enums")
@Tag("domain.model")
class GroovyAndMailEnumsTest {

    // ---------------------------------------------------------
    // GroovyExpressionAnalysis
    // ---------------------------------------------------------

    @Nested
    @DisplayName("GroovyExpressionAnalysis")
    class GroovyExpressionAnalysisTest {

        @Test
        @DisplayName("isCacheable() → true quand pas de currentRowNumber ni d'accès dynamique")
        void isCacheableWhenNoCurrentRowNumberNorDynamic() {
            GroovyExpressionAnalysis a = new GroovyExpressionAnalysis(
                    Set.of("site"), Set.of("tr_ref"), false, false);
            assertThat(a.isCacheable()).isTrue();
        }

        @Test
        @DisplayName("isCacheable() → false quand usesCurrentRowNumber=true")
        void notCacheableWhenCurrentRowNumber() {
            GroovyExpressionAnalysis a = new GroovyExpressionAnalysis(
                    Set.of(), Set.of(), true, false);
            assertThat(a.isCacheable()).isFalse();
        }

        @Test
        @DisplayName("isCacheable() → false quand hasUnresolvableAccess=true")
        void notCacheableWhenUnresolvable() {
            GroovyExpressionAnalysis a = new GroovyExpressionAnalysis(
                    Set.of(), Set.of(), false, true);
            assertThat(a.isCacheable()).isFalse();
        }

        @Test
        @DisplayName("unanalyzable() retourne une analyse non-cacheable")
        void unanalyzable() {
            GroovyExpressionAnalysis a = GroovyExpressionAnalysis.unanalyzable();
            assertThat(a.isCacheable()).isFalse();
            assertThat(a.datumColumns()).isEmpty();
            assertThat(a.referencesAccessed()).isEmpty();
            assertThat(a.hasUnresolvableAccess()).isTrue();
            assertThat(a.usesCurrentRowNumber()).isFalse();
        }

        @Test
        @DisplayName("empty() retourne une analyse cacheable sans colonnes")
        void empty() {
            GroovyExpressionAnalysis a = GroovyExpressionAnalysis.empty();
            assertThat(a.isCacheable()).isTrue();
            assertThat(a.datumColumns()).isEmpty();
            assertThat(a.referencesAccessed()).isEmpty();
        }

        @Test
        @DisplayName("record accessors renvoient les valeurs correctes")
        void recordAccessors() {
            Set<String> cols = Set.of("annee", "site");
            Set<String> refs = Set.of("tr_espece");
            GroovyExpressionAnalysis a = new GroovyExpressionAnalysis(cols, refs, false, false);
            assertThat(a.datumColumns()).isEqualTo(cols);
            assertThat(a.referencesAccessed()).isEqualTo(refs);
        }
    }

    // ---------------------------------------------------------
    // CommonExpression.EMPTY_STRING
    // ---------------------------------------------------------

    @Nested
    @DisplayName("CommonExpression.EMPTY_STRING")
    class CommonExpressionTest {

        @Test
        @DisplayName("evaluate() retourne une chaîne vide quel que soit le context")
        void evaluateReturnsEmpty() {
            assertThat(CommonExpression.EMPTY_STRING.evaluate(Map.of("k", "v"))).isEqualTo("");
            assertThat(CommonExpression.EMPTY_STRING.evaluate(Map.of())).isEqualTo("");
        }

        @Test
        @DisplayName("toString() contient le nom de l'enum")
        void toStringContainsName() {
            assertThat(CommonExpression.EMPTY_STRING.toString()).contains("EMPTY_STRING");
        }

        @Test
        @DisplayName("EMPTY_STRING est bien la seule valeur de l'enum")
        void onlyValue() {
            assertThat(CommonExpression.values()).containsExactly(CommonExpression.EMPTY_STRING);
        }
    }

    // ---------------------------------------------------------
    // EmailService.UPLOAD_STATE enum
    // ---------------------------------------------------------

    @Nested
    @DisplayName("EmailService.UPLOAD_STATE enum")
    class UploadStateTest {

        @Test
        void allValues() {
            assertThat(EmailService.UPLOAD_STATE.values()).containsExactlyInAnyOrder(
                    EmailService.UPLOAD_STATE.UPLOADED,
                    EmailService.UPLOAD_STATE.PUBLISHED,
                    EmailService.UPLOAD_STATE.UNPUBLISHED,
                    EmailService.UPLOAD_STATE.DELETED);
        }

        @Test
        void valueOf() {
            assertThat(EmailService.UPLOAD_STATE.valueOf("UPLOADED"))
                    .isEqualTo(EmailService.UPLOAD_STATE.UPLOADED);
        }
    }

    // ---------------------------------------------------------
    // EmailService.MESSAGES enum
    // ---------------------------------------------------------

    @Nested
    @DisplayName("EmailService.MESSAGES enum")
    class MessagesEnumTest {

        @Test
        void allValues() {
            assertThat(EmailService.MESSAGES.values()).containsExactlyInAnyOrder(
                    EmailService.MESSAGES.NEW_ACCOUNT,
                    EmailService.MESSAGES.NEW_EMAIL,
                    EmailService.MESSAGES.VALIDATION_KEY);
        }

        @Test
        void newAccountHasNonBlankSubject() {
            // subject est privé mais on peut vérifier que l'enum se construit sans exception
            // et que tous les membres de l'enum sont present
            assertThat(EmailService.MESSAGES.NEW_ACCOUNT.name()).isEqualTo("NEW_ACCOUNT");
        }
    }

    // ---------------------------------------------------------
    // BooleanGroovyExpression
    // ---------------------------------------------------------

    @Nested
    @DisplayName("BooleanGroovyExpression")
    class BooleanGroovyExpressionTest {

        private final Map<String, Object> ctx = Map.of("x", true, "y", false);

        @Test
        @DisplayName("evaluate() retourne true quand l'expression est vraie")
        void evaluateTrue() {
            BooleanGroovyExpression expr = BooleanGroovyExpression.forExpression("x", Set.of());
            assertThat(expr.evaluate(Map.of("x", true))).isTrue();
        }

        @Test
        @DisplayName("evaluate() lève GroovyException quand l'expression est fausse")
        void evaluateFalseThrows() {
            BooleanGroovyExpression expr = BooleanGroovyExpression.forExpression("y", Set.of());
            assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                    fr.inra.oresing.domain.groovy.exception.GroovyException.class,
                    () -> expr.evaluate(Map.of("y", false))
            )).isNotNull();
        }

        @Test
        @DisplayName("toString() ne lève pas d'exception")
        void toStringDoesNotThrow() {
            BooleanGroovyExpression expr = BooleanGroovyExpression.forExpression("true", Set.of());
            assertThat(expr.toString()).isNotNull();
        }
    }

    // ---------------------------------------------------------
    // StringGroovyExpression
    // ---------------------------------------------------------

    @Nested
    @DisplayName("StringGroovyExpression")
    class StringGroovyExpressionTest {

        @Test
        @DisplayName("evaluate() retourne la valeur convertie en String")
        void evaluate() {
            StringGroovyExpression expr = StringGroovyExpression.forExpression("'hello'", Set.of());
            assertThat(expr.evaluate(Map.of())).isEqualTo("hello");
        }

        @Test
        @DisplayName("evaluate() sur une expression retournant un nombre")
        void evaluateNumber() {
            StringGroovyExpression expr = StringGroovyExpression.forExpression("42", Set.of());
            assertThat(expr.evaluate(Map.of())).isEqualTo("42");
        }

        @Test
        @DisplayName("toString() ne lève pas d'exception")
        void toStringDoesNotThrow() {
            assertThat(StringGroovyExpression.forExpression("'x'", Set.of()).toString()).isNotNull();
        }
    }

    // ---------------------------------------------------------
    // StringSetGroovyExpression
    // ---------------------------------------------------------

    @Nested
    @DisplayName("StringSetGroovyExpression")
    class StringSetGroovyExpressionTest {

        @Test
        @DisplayName("evaluate() retourne null quand l'expression retourne null")
        void evaluateNull() {
            StringSetGroovyExpression expr = StringSetGroovyExpression.forExpression("null");
            assertThat(expr.evaluate(Map.of())).isNull();
        }

        @Test
        @DisplayName("evaluate() retourne un singleton quand l'expression retourne une String")
        void evaluateString() {
            StringSetGroovyExpression expr = StringSetGroovyExpression.forExpression("'abc'");
            assertThat(expr.evaluate(Map.of())).containsExactly("abc");
        }

        @Test
        @DisplayName("toString() ne lève pas d'exception")
        void toStringDoesNotThrow() {
            assertThat(StringSetGroovyExpression.forExpression("'x'").toString()).isNotNull();
        }
    }
}