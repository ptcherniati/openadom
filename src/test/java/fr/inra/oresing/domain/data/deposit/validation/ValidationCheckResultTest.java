package fr.inra.oresing.domain.data.deposit.validation;

import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.NullType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultCheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.GroovyValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des classes du package domain.data.deposit.validation.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("ValidationCheckResult implementations")
class ValidationCheckResultTest {

    private static final DataColumn COLUMN = new DataColumn("test_col");

    // ─── DuplicationLineValidationCheckResult ─────────────────────────────────

    @Nested
    @DisplayName("DuplicationLineValidationCheckResult")
    class DuplicationLineValidationCheckResultTest {

        @Test
        @DisplayName("constructeur avec FileType.REFERENCES utilise le message référence")
        void referencesFileType() {
            DuplicationLineValidationCheckResult result = new DuplicationLineValidationCheckResult(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES,
                    "myfile.csv",
                    ValidationLevel.ERROR,
                    Ltree.fromSql("a.b"),
                    42L,
                    ImmutableSortedSet.of(10L, 20L),
                    COLUMN
            );
            assertThat(result.message()).isEqualTo(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES);
            assertThat(result.level()).isEqualTo(ValidationLevel.ERROR);
        }

        @Test
        @DisplayName("messageParams contient file, lineNumber, otherLines, duplicateKey")
        void messageParamsContainsExpectedKeys() {
            DuplicationLineValidationCheckResult result = new DuplicationLineValidationCheckResult(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES,
                    "data.csv",
                    ValidationLevel.WARN,
                    Ltree.fromSql("parent.child"),
                    5L,
                    ImmutableSortedSet.of(1L, 3L),
                    COLUMN
            );
            assertThat(result.messageParams())
                    .containsKey("file")
                    .containsKey("lineNumber")
                    .containsKey("otherLines")
                    .containsKey("duplicateKey");
            assertThat(result.messageParams().get("file")).isEqualTo("data.csv");
            assertThat(result.messageParams().get("lineNumber")).isEqualTo(5L);
        }

        @Test
        @DisplayName("FileType enum contient REFERENCES et DATATYPE")
        void fileTypeEnumValues() {
            assertThat(DuplicationLineValidationCheckResult.FileType.values())
                    .containsExactlyInAnyOrder(
                            DuplicationLineValidationCheckResult.FileType.REFERENCES,
                            DuplicationLineValidationCheckResult.FileType.DATATYPE
                    );
        }

        @Test
        @DisplayName("MESSAGE_FOR_REFERENCES et MESSAGE_FOR_DATATYPES sont définis")
        void messageConstants() {
            assertThat(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES).isNotBlank();
            assertThat(DuplicationLineValidationCheckResult.MESSAGE_FOR_DATATYPES).isNotBlank();
        }
    }

    // ─── GroovyValidationCheckResult ──────────────────────────────────────────

    @Nested
    @DisplayName("GroovyValidationCheckResult")
    class GroovyValidationCheckResultTest {

        @Test
        @DisplayName("success() avec StringType retourne SUCCESS sans message ni params")
        void successWithStringType() {
            StringType st = StringType.getStringTypeFromStringValue("hello");
            DefaultCheckerValidationCheckResult checkerResult =
                    DefaultCheckerValidationCheckResult.success(COLUMN, st);
            GroovyValidationCheckResult result = GroovyValidationCheckResult.success(COLUMN, checkerResult.value());
            assertThat(result.level()).isEqualTo(ValidationLevel.SUCCESS);
            assertThat(result.message()).isNull();
            assertThat(result.messageParams()).isNull();
            assertThat(result.target()).isEqualTo(COLUMN);
            assertThat(result.value()).isNotNull();
        }

        @Test
        @DisplayName("error() retourne ERROR avec message et params")
        void errorResult() {
            com.google.common.collect.ImmutableMap<String, Object> params =
                    com.google.common.collect.ImmutableMap.of("key", "val");
            GroovyValidationCheckResult result =
                    GroovyValidationCheckResult.error(COLUMN, "errorMsg", params);
            assertThat(result.level()).isEqualTo(ValidationLevel.ERROR);
            assertThat(result.message()).isEqualTo("errorMsg");
            assertThat(result.messageParams()).containsEntry("key", "val");
            assertThat(result.value()).isNull();
        }

        @Test
        @DisplayName("success() avec NullType lève ClassCastException (cas non supporté)")
        void successWithNullTypeThrows() {
            // NullType ne peut pas être casté en StringType dans success()
            org.junit.jupiter.api.Assertions.assertThrows(ClassCastException.class,
                    () -> GroovyValidationCheckResult.success(COLUMN, NullType.INSTANCE));
        }

        @Test
        @DisplayName("record accessors fonctionnent correctement")
        void recordAccessors() {
            StringType st = StringType.getStringTypeFromStringValue("x");
            GroovyValidationCheckResult r = new GroovyValidationCheckResult(
                    ValidationLevel.SUCCESS, null, null, COLUMN, st);
            assertThat(r.level()).isEqualTo(ValidationLevel.SUCCESS);
            assertThat(r.message()).isNull();
            assertThat(r.target()).isEqualTo(COLUMN);
            assertThat(r.value()).isEqualTo(st);
        }
    }

    // ─── DefaultValidationCheckResult ────────────────────────────────────────

    @Nested
    @DisplayName("DefaultValidationCheckResult factory methods")
    class DefaultValidationCheckResultTest {

        @Test
        @DisplayName("success() retourne un résultat SUCCESS avec message null")
        void successHasNullMessage() {
            ValidationCheckResult r = DefaultValidationCheckResult.success(COLUMN);
            assertThat(r.level()).isEqualTo(ValidationLevel.SUCCESS);
            assertThat(r.message()).isNull();
            assertThat(r.messageParams()).isNull();
        }

        @Test
        @DisplayName("warn() retourne un résultat WARN avec message et params")
        void warnHasMessageAndParams() {
            ValidationCheckResult r = DefaultValidationCheckResult.warn(
                    "warning", com.google.common.collect.ImmutableMap.of("k", "v"), COLUMN);
            assertThat(r.level()).isEqualTo(ValidationLevel.WARN);
            assertThat(r.message()).isEqualTo("warning");
            assertThat(r.messageParams()).containsEntry("k", "v");
        }

        @Test
        @DisplayName("error() retourne un résultat ERROR")
        void errorHasErrorLevel() {
            ValidationCheckResult r = DefaultValidationCheckResult.error(
                    "err", Map.of("a", "b"), COLUMN);
            assertThat(r.level()).isEqualTo(ValidationLevel.ERROR);
            assertThat(r.message()).isEqualTo("err");
        }

        @Test
        @DisplayName("constructeur de copie depuis ValidationCheckResult")
        void copyConstructor() {
            ValidationCheckResult orig = DefaultValidationCheckResult.error("e", Map.of(), COLUMN);
            DefaultValidationCheckResult copy = new DefaultValidationCheckResult(orig);
            assertThat(copy.level()).isEqualTo(orig.level());
            assertThat(copy.message()).isEqualTo(orig.message());
        }
    }
}
