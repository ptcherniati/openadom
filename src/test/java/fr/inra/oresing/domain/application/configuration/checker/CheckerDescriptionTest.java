package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

class CheckerDescriptionTest {

    @Test
    void testStringCheckerBuilder() {
        StringChecker checker = CheckerDescriptionBuilder.stringChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .pattern("[a-z]+")
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.StringChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals("[a-z]+", checker.pattern());
    }

    @Test
    void testIntegerCheckerBuilder() {
        IntegerChecker checker = CheckerDescriptionBuilder.integerChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .min(1)
                .max(10)
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.IntegerChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals(1, checker.min());
        Assertions.assertEquals(10, checker.max());
    }

    @Test
    void testFloatCheckerBuilder() {
        FloatChecker checker = CheckerDescriptionBuilder.floatChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .min(1.0f)
                .max(10.0f)
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.FloatChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals(1.0f, checker.min());
        Assertions.assertEquals(10.0f, checker.max());
    }

    @Test
    void testDateCheckerBuilder() {
        LocalDate min = LocalDate.of(2020, 1, 1);
        LocalDate max = LocalDate.of(2020, 12, 31);
        DateChecker checker = CheckerDescriptionBuilder.dateChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .pattern("yyyy-MM-dd")
                .min(min)
                .max(max)
                .duration("P1D")
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.DateChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals("yyyy-MM-dd", checker.pattern());
        Assertions.assertEquals(min, checker.min());
        Assertions.assertEquals(max, checker.max());
        Assertions.assertEquals("P1D", checker.duration());
    }

    @Test
    void testBooleanCheckerBuilder() {
        BooleanChecker checker = CheckerDescriptionBuilder.booleanChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .isTrue(true)
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.BooleanChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertTrue(checker.isTrue());
    }

    @Test
    void testReferenceCheckerBuilder() {
        ReferenceChecker checker = CheckerDescriptionBuilder.referenceChecker()
                .componentKey("compKey")
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .refType("refType")
                .isRecursive(true)
                .isParent(true)
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.ReferenceChecker, checker.type());
        Assertions.assertEquals("compKey", checker.componentKey());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals("refType", checker.refType());
        Assertions.assertTrue(checker.isRecursive());
        Assertions.assertTrue(checker.isParent());
    }

    @Test
    void testComputationCheckerBuilder() {
        Set<String> references = Set.of("ref1", "ref2");
        Set<String> exceptionMessages = Set.of("error1");
        ComputationChecker checker = CheckerDescriptionBuilder.computationChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .expression("expr")
                .references(references)
                .exceptionMessages(exceptionMessages)
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.ComputationChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals("expr", checker.expression());
        Assertions.assertEquals(references, checker.references());
        Assertions.assertEquals(exceptionMessages, checker.exceptionMessages());
    }

    @Test
    void testGroovyExpressionCheckerBuilder() {
        Set<String> references = Set.of("ref1", "ref2");
        Set<String> exceptionMessages = Set.of("error1");
        GroovyExpressionChecker checker = CheckerDescriptionBuilder.groovyExpressionChecker()
                .multiplicity(Multiplicity.ONE)
                .required(true)
                .expression("expr")
                .references(references)
                .exceptionMessages(exceptionMessages)
                .build();

        Assertions.assertEquals(CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker, checker.type());
        Assertions.assertEquals(Multiplicity.ONE, checker.multiplicity());
        Assertions.assertTrue(checker.required());
        Assertions.assertEquals("expr", checker.expression());
        Assertions.assertEquals(references, checker.references());
        Assertions.assertEquals(exceptionMessages, checker.exceptionMessages());
    }
}