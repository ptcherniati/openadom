package fr.inra.oresing.domain.data.deposit.validation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des classes critiques du pipeline de validation (dépôt CSV).
 * Aucun contexte Spring — exécution ultra-rapide.
 */
@Tag("domain.model")
@DisplayName("Pipeline de validation — classes critiques")
class CriticalValidationPipelineTest {

    private static final DataColumn COLUMN = new DataColumn("col");

    // ─── CsvRowValidationCheckResult ─────────────────────────────────────────

    @Nested
    @DisplayName("CsvRowValidationCheckResult")
    class CsvRowValidationCheckResultTests {

        @Test
        @DisplayName("constructeur principal stocke validationCheckResult et lineNumber")
        void mainConstructorStoresFields() {
            ValidationCheckResult inner = DefaultValidationCheckResult.success(COLUMN);
            CsvRowValidationCheckResult result = new CsvRowValidationCheckResult(inner, 7L);

            assertThat(result.validationCheckResult()).isSameAs(inner);
            assertThat(result.lineNumber()).isEqualTo(7L);
        }

        @Test
        @DisplayName("constructeur spécialisé avec DuplicationLineValidationCheckResult enveloppe dans DefaultValidationCheckResult")
        void duplicationConstructorWrapsInDefault() {
            DuplicationLineValidationCheckResult dup = new DuplicationLineValidationCheckResult(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES,
                    "file.csv",
                    ValidationLevel.ERROR,
                    Ltree.fromSql("root.child"),
                    3L,
                    ImmutableSortedSet.of(1L, 2L),
                    COLUMN
            );
            CsvRowValidationCheckResult result = new CsvRowValidationCheckResult(dup, 3L);

            assertThat(result.lineNumber()).isEqualTo(3L);
            assertThat(result.validationCheckResult()).isInstanceOf(DefaultValidationCheckResult.class);
            assertThat(result.validationCheckResult().level()).isEqualTo(ValidationLevel.ERROR);
            assertThat(result.validationCheckResult().message())
                    .isEqualTo(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES);
        }

        @Test
        @DisplayName("lineNumber = 0 est une valeur valide")
        void lineNumberZeroIsValid() {
            CsvRowValidationCheckResult result = new CsvRowValidationCheckResult(
                    DefaultValidationCheckResult.success(COLUMN), 0L);
            assertThat(result.lineNumber()).isZero();
        }
    }

    // ─── DefaultValidationCheckResult ────────────────────────────────────────

    @Nested
    @DisplayName("DefaultValidationCheckResult")
    class DefaultValidationCheckResultTests {

        @Test
        @DisplayName("constructeur de copie copie tous les champs")
        void copyConstructorCopiesAllFields() {
            ValidationCheckResult orig = DefaultValidationCheckResult.error(
                    "origMsg", Map.of("k", "v"), COLUMN);
            DefaultValidationCheckResult copy = new DefaultValidationCheckResult(orig);

            assertThat(copy.level()).isEqualTo(orig.level());
            assertThat(copy.message()).isEqualTo(orig.message());
            assertThat(copy.messageParams()).isEqualTo(orig.messageParams());
            assertThat(copy.target()).isEqualTo(orig.target());
        }

        @Test
        @DisplayName("constructeur direct stocke les quatre champs")
        void directConstructorStoresAllFields() {
            Map<String, Object> params = Map.of("x", 1);
            DefaultValidationCheckResult r = new DefaultValidationCheckResult(
                    ValidationLevel.WARN, "msg", params, COLUMN);

            assertThat(r.level()).isEqualTo(ValidationLevel.WARN);
            assertThat(r.message()).isEqualTo("msg");
            assertThat(r.messageParams()).isEqualTo(params);
            assertThat(r.target()).isEqualTo(COLUMN);
        }

        @Test
        @DisplayName("success() produit SUCCESS avec message et params null")
        void successIsSuccessWithNulls() {
            ValidationCheckResult r = DefaultValidationCheckResult.success(COLUMN);

            assertThat(r.level()).isEqualTo(ValidationLevel.SUCCESS);
            assertThat(r.message()).isNull();
            assertThat(r.messageParams()).isNull();
            assertThat(r.target()).isEqualTo(COLUMN);
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.isError()).isFalse();
        }

        @Test
        @DisplayName("warn() produit WARN avec message et params")
        void warnCarriesMessageAndParams() {
            ImmutableMap<String, Object> params = ImmutableMap.of("p", "q");
            ValidationCheckResult r = DefaultValidationCheckResult.warn("warnMsg", params, COLUMN);

            assertThat(r.level()).isEqualTo(ValidationLevel.WARN);
            assertThat(r.message()).isEqualTo("warnMsg");
            assertThat(r.messageParams()).containsEntry("p", "q");
            assertThat(r.target()).isEqualTo(COLUMN);
        }

        @Test
        @DisplayName("error() produit ERROR avec message et params")
        void errorCarriesMessageAndParams() {
            Map<String, Object> params = Map.of("a", "b");
            ValidationCheckResult r = DefaultValidationCheckResult.error("errMsg", params, COLUMN);

            assertThat(r.level()).isEqualTo(ValidationLevel.ERROR);
            assertThat(r.message()).isEqualTo("errMsg");
            assertThat(r.messageParams()).containsEntry("a", "b");
            assertThat(r.target()).isEqualTo(COLUMN);
            assertThat(r.isError()).isTrue();
        }
    }

    // ─── ValidationCheckResultRest ───────────────────────────────────────────

    @Nested
    @DisplayName("ValidationCheckResultRest")
    class ValidationCheckResultRestTests {

        @Test
        @DisplayName("constructeur principal stocke tous les champs")
        void mainConstructorStoresAllFields() {
            Map<String, Object> params = Map.of("k", "v");
            ValidationCheckResultRest r = new ValidationCheckResultRest("MyType", "msg", params, 5L);

            assertThat(r.type()).isEqualTo("MyType");
            assertThat(r.message()).isEqualTo("msg");
            assertThat(r.params()).isEqualTo(params);
            assertThat(r.lineNumber()).isEqualTo(5L);
        }

        @Test
        @DisplayName("constructeur court définit lineNumber = -1")
        void shortConstructorSetsLineNumberToMinusOne() {
            ValidationCheckResultRest r = new ValidationCheckResultRest("T", "m", null);

            assertThat(r.lineNumber()).isEqualTo(-1L);
            assertThat(r.type()).isEqualTo("T");
            assertThat(r.message()).isEqualTo("m");
            assertThat(r.params()).isNull();
        }

        @Test
        @DisplayName("withLineNumber retourne une nouvelle instance avec le nouveau numéro de ligne")
        void withLineNumberReturnsNewInstanceWithGivenNumber() {
            ValidationCheckResultRest original = new ValidationCheckResultRest("T", "m", Map.of("x", 1), 10L);
            ValidationCheckResultRest updated = original.withLineNumber(99L);

            assertThat(updated.lineNumber()).isEqualTo(99L);
            assertThat(updated.type()).isEqualTo(original.type());
            assertThat(updated.message()).isEqualTo(original.message());
            assertThat(updated.params()).isEqualTo(original.params());
        }

        @Test
        @DisplayName("withLineNumber ne modifie pas l'instance originale (immutabilité)")
        void withLineNumberDoesNotMutateOriginal() {
            ValidationCheckResultRest original = new ValidationCheckResultRest("T", "m", null, 1L);
            original.withLineNumber(42L);

            assertThat(original.lineNumber()).isEqualTo(1L);
        }
    }

    // ─── DuplicationLineValidationCheckResult ────────────────────────────────

    @Nested
    @DisplayName("DuplicationLineValidationCheckResult")
    class DuplicationLineValidationCheckResultTests {

        @Test
        @DisplayName("FileType.REFERENCES produit le message référence")
        void referencesFileTypeUsesReferenceMessage() {
            DuplicationLineValidationCheckResult r = buildWith(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES, ValidationLevel.ERROR);

            assertThat(r.message()).isEqualTo(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES);
        }

        @Test
        @DisplayName("FileType.DATATYPE utilise toujours le message référence (comportement actuel du constructeur)")
        void datatypeFileTypeAlsoUsesReferenceMessage() {
            // Le constructeur teste FileType.REFERENCES.message != null, donc les deux filetype
            // produisent MESSAGE_FOR_REFERENCES avec l'implémentation actuelle.
            DuplicationLineValidationCheckResult r = buildWith(
                    DuplicationLineValidationCheckResult.FileType.DATATYPE, ValidationLevel.WARN);

            assertThat(r.message()).isEqualTo(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES);
        }

        @Test
        @DisplayName("level() reflète le niveau passé au constructeur")
        void levelReflectsConstructorArgument() {
            assertThat(buildWith(DuplicationLineValidationCheckResult.FileType.REFERENCES, ValidationLevel.WARN)
                    .level()).isEqualTo(ValidationLevel.WARN);
            assertThat(buildWith(DuplicationLineValidationCheckResult.FileType.REFERENCES, ValidationLevel.ERROR)
                    .level()).isEqualTo(ValidationLevel.ERROR);
        }

        @Test
        @DisplayName("messageParams contient file, lineNumber, otherLines, duplicateKey")
        void messageParamsContainsMandatoryKeys() {
            DuplicationLineValidationCheckResult r = new DuplicationLineValidationCheckResult(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES,
                    "myFile.csv",
                    ValidationLevel.ERROR,
                    Ltree.fromSql("root.child"),
                    42L,
                    ImmutableSortedSet.of(1L, 2L),
                    COLUMN
            );

            assertThat(r.messageParams())
                    .containsKey("file")
                    .containsKey("lineNumber")
                    .containsKey("otherLines")
                    .containsKey("duplicateKey");
            assertThat(r.messageParams().get("file")).isEqualTo("myFile.csv");
            assertThat(r.messageParams().get("lineNumber")).isEqualTo(42L);
            assertThat(r.messageParams().get("duplicateKey")).isEqualTo("root.child");
        }

        @Test
        @DisplayName("messageParams.otherLines contient les lignes dupliquées")
        void messageParamsOtherLinesMatchInput() {
            ImmutableSortedSet<Long> lines = ImmutableSortedSet.of(10L, 20L, 30L);
            DuplicationLineValidationCheckResult r = new DuplicationLineValidationCheckResult(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES,
                    "f.csv", ValidationLevel.ERROR, Ltree.fromSql("k"), 5L, lines, COLUMN);

            assertThat(r.messageParams().get("otherLines")).isEqualTo(lines);
        }

        @Test
        @DisplayName("target() retourne la CheckerTarget passée au constructeur")
        void targetIsPreserved() {
            DuplicationLineValidationCheckResult r = buildWith(
                    DuplicationLineValidationCheckResult.FileType.REFERENCES, ValidationLevel.ERROR);

            assertThat(r.target()).isEqualTo(COLUMN);
        }

        @Test
        @DisplayName("MESSAGE_FOR_REFERENCES et MESSAGE_FOR_DATATYPES sont définis et distincts")
        void messageConstantsAreDefinedAndDistinct() {
            assertThat(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES).isNotBlank();
            assertThat(DuplicationLineValidationCheckResult.MESSAGE_FOR_DATATYPES).isNotBlank();
            assertThat(DuplicationLineValidationCheckResult.MESSAGE_FOR_REFERENCES)
                    .isNotEqualTo(DuplicationLineValidationCheckResult.MESSAGE_FOR_DATATYPES);
        }

        private DuplicationLineValidationCheckResult buildWith(
                DuplicationLineValidationCheckResult.FileType fileType,
                ValidationLevel level) {
            return new DuplicationLineValidationCheckResult(
                    fileType, "file.csv", level,
                    Ltree.fromSql("root.child"), 1L,
                    ImmutableSortedSet.of(1L, 2L), COLUMN);
        }
    }
}
