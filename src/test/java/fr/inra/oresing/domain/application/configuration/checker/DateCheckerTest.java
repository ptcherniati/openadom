package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class DateCheckerTest {

    @Test
    void testComment() {
        DateChecker checker = new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd", null, null, null);
        Assertions.assertEquals("yyyy-MM-dd Date", checker.comment());
    }

    @Test
    void testBuildImportDataExempleForheader() {
        DateChecker checker = new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd", null, null, null);
        Assertions.assertEquals("a date with pattern yyyy-MM-dd", checker.buildImportDataExempleForheader());
    }
}