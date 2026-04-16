package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
class ReferenceCheckerTest {

    @Test
    void testComment() {
        ReferenceChecker checker = new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker, "myComponent", Multiplicity.ONE, false, "myRef", false, false);
        Assertions.assertEquals("myRef Reference", checker.comment());
    }

    @Test
    void testBuildImportDataExempleForheader() {
        ReferenceChecker checker = new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker, "myComponent", Multiplicity.ONE, false, "myRef", false, false);
        Assertions.assertEquals("A value of myRef", checker.buildImportDataExempleForheader());
    }
}