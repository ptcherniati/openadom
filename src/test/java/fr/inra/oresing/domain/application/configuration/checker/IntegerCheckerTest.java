package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class IntegerCheckerTest {

    @Test
    void testComment() {
        IntegerChecker checker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, false, null, null);
        Assertions.assertThat(checker.comment()).isEqualTo("Integer");
    }

    @Test
    void testBuildImportDataExempleForheader() {
        IntegerChecker checker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, false, null, null);
        Assertions.assertThat(checker.buildImportDataExempleForheader()).isEqualTo("an integer");
    }
}