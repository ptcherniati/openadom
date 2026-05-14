package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

@Tag("core.config")
@Tag("domain.model")
class GroovyExpressionCheckerTest {

    @Test
    void testIsCodify() {
        GroovyExpressionChecker checker = new GroovyExpressionChecker(CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker, Multiplicity.ONE, false, null, null, null);
        Assertions.assertTrue(checker.isCodify());
    }

    @Test
    void testDatatypes() {
        Set<String> references = Set.of("ref1", "ref2");
        GroovyExpressionChecker checker = new GroovyExpressionChecker(CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker, Multiplicity.ONE, false, null, references, null);
        Assertions.assertEquals(references, checker.datatypes());
    }
}