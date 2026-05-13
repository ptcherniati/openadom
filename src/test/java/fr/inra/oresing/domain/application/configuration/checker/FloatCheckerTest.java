package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class FloatCheckerTest {

    @Test
    void testComment() {
        FloatChecker checker = new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker, Multiplicity.ONE, false, null, null);
        Assertions.assertEquals("Float", checker.comment());
    }

    @Test
    void testBuildImportDataExempleForheader() {
        FloatChecker checker = new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker, Multiplicity.ONE, false, null, null);
        Assertions.assertEquals("a float", checker.buildImportDataExempleForheader());
    }
}