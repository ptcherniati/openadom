package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.junit.Assert;
import org.junit.Test;

public class RegularExpressionCheckerTest {

    private RegularExpressionChecker buildChecker(CheckerTarget target, String pattern) {
        RegularExpressionCheckerConfiguration configuration = new RegularExpressionCheckerConfiguration() {
            @Override
            public String getPattern() {
                return pattern;
            }

            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public fr.inra.oresing.transformer.TransformationConfiguration getTransformation() {
                return null;
            }
        };
        return new RegularExpressionChecker(target, pattern, configuration, null);
    }

    @Test
    public void testMatchingValue() {
        RegularExpressionChecker checker = buildChecker(new VariableComponentKey("mesure", "code"), "[A-Z]{3}");
        Assert.assertTrue(checker.check("ABC").isSuccess());
    }

    @Test
    public void testNonMatchingValue() {
        RegularExpressionChecker checker = buildChecker(new VariableComponentKey("mesure", "code"), "[A-Z]{3}");
        ValidationCheckResult result = checker.check("abc");
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testDigitPattern() {
        RegularExpressionChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"), "\\d+");
        Assert.assertTrue(checker.check("12345").isSuccess());
        Assert.assertFalse(checker.check("123abc").isSuccess());
    }

    @Test
    public void testIsValidWithValidPattern() {
        Assert.assertTrue(RegularExpressionChecker.isValid("[a-z]+"));
        Assert.assertTrue(RegularExpressionChecker.isValid("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void testIsValidWithInvalidPattern() {
        Assert.assertFalse(RegularExpressionChecker.isValid("[unclosed"));
    }

    @Test
    public void testIsValidWithBlankPattern() {
        Assert.assertFalse(RegularExpressionChecker.isValid(""));
        Assert.assertFalse(RegularExpressionChecker.isValid("   "));
        Assert.assertFalse(RegularExpressionChecker.isValid(null));
    }

    @Test
    public void testSqlTypeIsText() {
        RegularExpressionChecker checker = buildChecker(new VariableComponentKey("mesure", "code"), ".*");
        Assert.assertEquals(fr.inra.oresing.persistence.SqlPrimitiveType.TEXT, checker.getSqlType());
    }
}
