package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
class IntegerCheckerTest {

    @Test
    void testComment() {
        IntegerChecker checker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, false, null, null);
        Assertions.assertEquals("Integer", checker.comment());
    }

    @Test
    void testBuildImportDataExempleForheader() {
        IntegerChecker checker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, false, null, null);
        Assertions.assertEquals("an integer", checker.buildImportDataExempleForheader());
    }
}