package fr.inra.oresing.checker;

import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.junit.Assert;
import org.junit.Test;

public class FloatCheckerTest {

    private FloatChecker buildChecker(CheckerTarget target) {
        FloatCheckerConfiguration configuration = new FloatCheckerConfiguration() {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public fr.inra.oresing.transformer.TransformationConfiguration getTransformation() {
                return null;
            }
        };
        return new FloatChecker(target, configuration, null);
    }

    @Test
    public void testValidFloatWithDot() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("3.14").isSuccess());
    }

    @Test
    public void testValidFloatWithComma() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("3,14").isSuccess());
    }

    @Test
    public void testValidNegativeFloat() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("-273.15").isSuccess());
    }

    @Test
    public void testValidIntegerAsFloat() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("42").isSuccess());
    }

    @Test
    public void testInvalidFloat() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        ValidationCheckResult result = checker.check("not-a-float");
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testInvalidFloatEmpty() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        ValidationCheckResult result = checker.check("abc");
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testSqlTypeIsNumeric() {
        FloatChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertEquals(fr.inra.oresing.persistence.SqlPrimitiveType.NUMERIC, checker.getSqlType());
    }

    @Test
    public void testCheckerTargetWithReferenceColumn() {
        ReferenceColumn column = new ReferenceColumn("temperature");
        FloatChecker checker = buildChecker(column);
        Assert.assertEquals(column, checker.getTarget());
        ValidationCheckResult result = checker.check("invalid");
        Assert.assertFalse(result.isSuccess());
    }
}
