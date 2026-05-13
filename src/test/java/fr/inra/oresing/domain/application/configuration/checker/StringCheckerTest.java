package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class StringCheckerTest {

    @Test
    void testCommentWithPattern() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, "mypattern");
        Assertions.assertEquals("mypattern String", checker.comment());
    }

    @Test
    void testCommentWithDefaultPattern() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, ".*");
        Assertions.assertEquals("String", checker.comment());
    }

    @Test
    void testCommentWithNullPattern() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, null);
        Assertions.assertEquals("String", checker.comment());
    }

    @Test
    void testBuildImportDataExempleForheaderWithPattern() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, "mypattern");
        Assertions.assertEquals("a string with pattern mypattern", checker.buildImportDataExempleForheader());
    }

    @Test
    void testBuildImportDataExempleForheaderWithDefaultPattern() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, ".*");
        Assertions.assertEquals("a string", checker.buildImportDataExempleForheader());
    }

    @Test
    void testBuildImportDataExempleForheaderWithNullPattern() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, null);
        Assertions.assertEquals("a string", checker.buildImportDataExempleForheader());
    }
}