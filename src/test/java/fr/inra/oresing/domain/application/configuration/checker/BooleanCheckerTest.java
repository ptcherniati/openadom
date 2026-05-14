package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class BooleanCheckerTest {

    @Test
    void testComment() {
        BooleanChecker checker = new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, Multiplicity.ONE, false, true);
        Assertions.assertEquals("Boolean", checker.comment());
    }

    @Test
    void testBuildImportDataExempleForheader() {
        BooleanChecker checker = new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, Multiplicity.ONE, false, true);
        Assertions.assertEquals("a boolean", checker.buildImportDataExempleForheader());
    }
}