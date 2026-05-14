package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class CheckerFactoryTest {

    @Test
    void testGetGroovyExpressionChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_groovyExpression");
        Assertions.assertInstanceOf(GroovyCheckerType.class, checkerType);
    }

    @Test
    void testGetBooleanChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_boolean");
        Assertions.assertInstanceOf(BooleanCheckerType.class, checkerType);
    }

    @Test
    void testGetDateChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_date");
        Assertions.assertInstanceOf(DateCheckerType.class, checkerType);
    }

    @Test
    void testGetFloatChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_float");
        Assertions.assertInstanceOf(FloatCheckerType.class, checkerType);
    }

    @Test
    void testGetIntegerChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_integer");
        Assertions.assertInstanceOf(IntegerCheckerType.class, checkerType);
    }

    @Test
    void testGetReferenceChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_reference");
        Assertions.assertInstanceOf(ReferenceCheckerType.class, checkerType);
    }

    @Test
    void testGetStringChecker() {
        CheckerType checkerType = CheckerFactory.getCheckerTypeForName("OA_string");
        Assertions.assertInstanceOf(StringCheckerType.class, checkerType);
    }

    @Test
    void testGetCheckerWithNullName() {
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> {
            CheckerFactory.getCheckerTypeForName(null);
        });
    }

    @Test
    void testGetCheckerWithEmptyName() {
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> {
            CheckerFactory.getCheckerTypeForName("");
        });
    }

    @Test
    void testGetCheckerWithUnknownName() {
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> {
            CheckerFactory.getCheckerTypeForName("unknown");
        });
    }
}