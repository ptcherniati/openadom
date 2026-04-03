package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

@Tag("core.config")
class ComputationCheckerTest {

    @Test
    void testIsCodify() {
        ComputationChecker checker = new ComputationChecker(CheckerDescription.CheckerDescriptionType.ComputationChecker, Multiplicity.ONE, false, null, null, null);
        Assertions.assertFalse(checker.isCodify());
    }

    @Test
    void testDatatypes() {
        Set<String> references = Set.of("ref1", "ref2");
        ComputationChecker checker = new ComputationChecker(CheckerDescription.CheckerDescriptionType.ComputationChecker, Multiplicity.ONE, false, null, references, null);
        Assertions.assertEquals(references, checker.datatypes());
    }
}