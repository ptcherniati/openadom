package fr.inra.oresing.checker;

import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.junit.Assert;
import org.junit.Test;

public class IntegerCheckerTest {

    private IntegerChecker buildChecker(CheckerTarget target) {
        IntegerCheckerConfiguration configuration = new IntegerCheckerConfiguration() {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public fr.inra.oresing.transformer.TransformationConfiguration getTransformation() {
                return null;
            }
        };
        return new IntegerChecker(target, configuration, null);
    }

    @Test
    public void testValidInteger() {
        IntegerChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("42").isSuccess());
    }

    @Test
    public void testValidNegativeInteger() {
        IntegerChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("-100").isSuccess());
    }

    @Test
    public void testValidZero() {
        IntegerChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertTrue(checker.check("0").isSuccess());
    }

    @Test
    public void testInvalidFloat() {
        IntegerChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        ValidationCheckResult result = checker.check("3.14");
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testInvalidText() {
        IntegerChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        ValidationCheckResult result = checker.check("not-an-integer");
        Assert.assertFalse(result.isSuccess());
    }

    @Test
    public void testSqlTypeIsInteger() {
        IntegerChecker checker = buildChecker(new VariableComponentKey("mesure", "valeur"));
        Assert.assertEquals(fr.inra.oresing.persistence.SqlPrimitiveType.INTEGER, checker.getSqlType());
    }

    @Test
    public void testCheckerTargetWithReferenceColumn() {
        ReferenceColumn column = new ReferenceColumn("count");
        IntegerChecker checker = buildChecker(column);
        Assert.assertEquals(column, checker.getTarget());
        ValidationCheckResult result = checker.check("1");
        Assert.assertTrue(result.isSuccess());
    }
}
