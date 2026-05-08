package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.type.PatternType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour StringValidationCheckResult et PatternValidationCheckResult.
 */
@Tag("domain.model")
@DisplayName("ValidationCheckResult – tests unitaires")
class ValidationCheckResultTest {

    private static final DataColumn TARGET_COL = new DataColumn("testCol");

    // =========================================================================
    //  StringValidationCheckResult
    // =========================================================================

    @Nested
    @DisplayName("StringValidationCheckResult")
    class StringValidationCheckResultTest {

        @Test
        @DisplayName("success() crée un résultat SUCCESS avec la valeur copiée")
        void success() {
            StringType value = StringType.getStringTypeFromStringValue("hello");
            StringValidationCheckResult result = StringValidationCheckResult.success(TARGET_COL, value);
            assertThat(result.level()).isEqualTo(ValidationLevel.SUCCESS);
            assertThat(result.message()).isNull();
            assertThat(result.value()).isNotNull();
            assertThat(result.target()).isEqualTo(TARGET_COL);
        }

        @Test
        @DisplayName("error() crée un résultat ERROR avec message")
        void error() {
            StringValidationCheckResult result = StringValidationCheckResult.error(
                    TARGET_COL, "bad_value", ImmutableMap.of("key", "val"));
            assertThat(result.level()).isEqualTo(ValidationLevel.ERROR);
            assertThat(result.message()).isEqualTo("bad_value");
            assertThat(result.messageParams()).containsEntry("key", "val");
            assertThat(result.value()).isNull();
        }

        @Test
        @DisplayName("isError() retourne false pour SUCCESS")
        void isErrorFalseForSuccess() {
            StringValidationCheckResult result = StringValidationCheckResult.success(
                    TARGET_COL, StringType.getStringTypeFromStringValue("v"));
            assertThat(result.isError()).isFalse();
        }

        @Test
        @DisplayName("isError() retourne true pour ERROR")
        void isErrorTrueForError() {
            StringValidationCheckResult result = StringValidationCheckResult.error(
                    TARGET_COL, "err", ImmutableMap.of());
            assertThat(result.isError()).isTrue();
        }
    }

    // =========================================================================
    //  PatternValidationCheckResult
    // =========================================================================

    @Nested
    @DisplayName("PatternValidationCheckResult")
    class PatternValidationCheckResultTestClass {

        @Test
        @DisplayName("of() propage l'erreur si le résultat entrant est une erreur")
        void ofWithError() {
            CheckerValidationCheckResult errorResult = StringValidationCheckResult.error(
                    TARGET_COL, "error_msg", ImmutableMap.of());
            Map<String, Object> emptyMap = new HashMap<>();
            PatternType<String, Object> patternType = new PatternType<>(emptyMap);
            CheckerValidationCheckResult result = PatternValidationCheckResult.of(errorResult, patternType);
            assertThat(result).isSameAs(errorResult);
            assertThat(result.isError()).isTrue();
        }

        @Test
        @DisplayName("of() crée un PatternValidationCheckResult pour un résultat SUCCESS")
        void ofWithSuccess() {
            StringType value = StringType.getStringTypeFromStringValue("test");
            CheckerValidationCheckResult successResult = StringValidationCheckResult.success(TARGET_COL, value);
            Map<String, Object> innerMap = new HashMap<>();
            PatternType<String, Object> patternType = new PatternType<>(innerMap);
            CheckerValidationCheckResult result = PatternValidationCheckResult.of(successResult, patternType);
            assertThat(result).isInstanceOf(PatternValidationCheckResult.class);
            assertThat(result.level()).isEqualTo(ValidationLevel.SUCCESS);
        }

        @Test
        @DisplayName("PatternValidationCheckResult direct : constructeur record")
        void directConstruction() {
            Map<String, Object> map = new HashMap<>();
            PatternType<String, Object> pt = new PatternType<>(map);
            PatternValidationCheckResult result = new PatternValidationCheckResult(
                    ValidationLevel.SUCCESS, null, null, TARGET_COL, pt);
            assertThat(result.level()).isEqualTo(ValidationLevel.SUCCESS);
            assertThat(result.target()).isEqualTo(TARGET_COL);
            assertThat(result.value()).isSameAs(pt);
        }
    }

    // =========================================================================
    //  DefaultManyValidationCheckResult
    // =========================================================================

    @Nested
    @DisplayName("DefaultManyValidationCheckResult")
    class DefaultManyValidationCheckResultTest {

        @Test
        @DisplayName("level() est ERROR si au moins un élément est ERROR")
        void levelIsErrorWhenAnyError() {
            StringValidationCheckResult ok = StringValidationCheckResult.success(
                    TARGET_COL, StringType.getStringTypeFromStringValue("v"));
            StringValidationCheckResult err = StringValidationCheckResult.error(
                    TARGET_COL, "err_msg", ImmutableMap.of());
            DefaultManyValidationCheckResult result = new DefaultManyValidationCheckResult(
                    java.util.List.of(ok, err), TARGET_COL);
            assertThat(result.level()).isEqualTo(ValidationLevel.ERROR);
        }

        @Test
        @DisplayName("level() est SUCCESS si tous sont SUCCESS")
        void levelIsSuccessWhenAllSuccess() {
            StringValidationCheckResult ok1 = StringValidationCheckResult.success(
                    TARGET_COL, StringType.getStringTypeFromStringValue("a"));
            StringValidationCheckResult ok2 = StringValidationCheckResult.success(
                    TARGET_COL, StringType.getStringTypeFromStringValue("b"));
            DefaultManyValidationCheckResult result = new DefaultManyValidationCheckResult(
                    java.util.List.of(ok1, ok2), TARGET_COL);
            assertThat(result.level()).isEqualTo(ValidationLevel.SUCCESS);
        }

        @Test
        @DisplayName("message() concatène les messages avec ';'")
        void messageConcatenates() {
            StringValidationCheckResult e1 = StringValidationCheckResult.error(
                    TARGET_COL, "msg1", ImmutableMap.of());
            StringValidationCheckResult e2 = StringValidationCheckResult.error(
                    TARGET_COL, "msg2", ImmutableMap.of());
            DefaultManyValidationCheckResult result = new DefaultManyValidationCheckResult(
                    java.util.List.of(e1, e2), TARGET_COL);
            assertThat(result.message()).contains("msg1").contains("msg2");
        }

        @Test
        @DisplayName("target() retourne la cible passée au constructeur")
        void targetIsCorrect() {
            DefaultManyValidationCheckResult result = new DefaultManyValidationCheckResult(
                    java.util.List.of(), TARGET_COL);
            assertThat(result.target()).isEqualTo(TARGET_COL);
        }

        @Test
        @DisplayName("getValidations() retourne this")
        void getValidationsReturnsSelf() {
            DefaultManyValidationCheckResult result = new DefaultManyValidationCheckResult(
                    java.util.List.of(), TARGET_COL);
            assertThat(result.getValidations()).isSameAs(result);
        }
    }
}