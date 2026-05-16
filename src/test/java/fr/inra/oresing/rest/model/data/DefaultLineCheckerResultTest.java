package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs de {@link DefaultLineCheckerResult} — aucun contexte Spring.
 * Couvre la méthode de fabrique {@link DefaultLineCheckerResult#fromLineChecker(LineChecker)}.
 */
@Tag("domain.model")
@DisplayName("DefaultLineCheckerResult — fromLineChecker()")
class DefaultLineCheckerResultTest {

    private static final DataColumn TARGET = new DataColumn("col");

    // ─────────────────────────────────────────────────────────────────────────
    //  fromLineChecker(OneChecker)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fromLineChecker(OneChecker) sans transformer — target et fieldTypeForOne corrects")
    void fromOneCheckerNullTransformer() {
        StringType st = StringType.getStringTypeFromStringValue("hello");
        LineChecker.OneChecker<StringType> oneChecker = new LineChecker.OneChecker<>(
                st,
                TARGET,
                LineChecker.LineTransformer.NULL_LINE_TRANSFORMER,
                CheckerDescription.NO_CHECKER
        );

        LineCheckerResult result = DefaultLineCheckerResult.fromLineChecker(oneChecker);

        assertThat(result).isInstanceOf(DefaultLineCheckerResult.class);
        DefaultLineCheckerResult<?, ?> dcr = (DefaultLineCheckerResult<?, ?>) result;
        assertThat(dcr.target()).isEqualTo(TARGET);
        assertThat(dcr.fieldTypeForOne()).isEqualTo(st);
        assertThat(dcr.value()).isNull();
        assertThat(dcr.transformer()).isNotNull(); // NULL_LINE_TRANSFORMER.toString() ≠ null
        assertThat(dcr.checkerDescription()).isEqualTo(CheckerDescription.NO_CHECKER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  fromLineChecker(ManyChecker)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fromLineChecker(ManyChecker) sans transformer — target, fieldTypeForOne et value corrects")
    void fromManyCheckerNullTransformer() {
        StringType st = StringType.getStringTypeFromStringValue("hello");
        fr.inra.oresing.domain.checker.type.ListType<StringType> listType =
                new fr.inra.oresing.domain.checker.type.ListType<>(st);
        LineChecker.ManyChecker<StringType, fr.inra.oresing.domain.checker.type.ListType<StringType>> manyChecker =
                new LineChecker.ManyChecker<>(
                        listType,
                        TARGET,
                        LineChecker.LineTransformer.NULL_LINE_TRANSFORMER,
                        CheckerDescription.NO_CHECKER
                );

        LineCheckerResult result = DefaultLineCheckerResult.fromLineChecker(manyChecker);

        assertThat(result).isInstanceOf(DefaultLineCheckerResult.class);
        DefaultLineCheckerResult<?, ?> dcr = (DefaultLineCheckerResult<?, ?>) result;
        assertThat(dcr.target()).isEqualTo(TARGET);
        assertThat(dcr.fieldTypeForOne()).isEqualTo(st);
        assertThat(dcr.value()).isEqualTo(listType);
        assertThat(dcr.transformer()).isNotNull(); // NULL_LINE_TRANSFORMER.toString() ≠ null
        assertThat(dcr.checkerDescription()).isEqualTo(CheckerDescription.NO_CHECKER);
    }
}
