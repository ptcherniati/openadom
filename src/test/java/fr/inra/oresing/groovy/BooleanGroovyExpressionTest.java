package fr.inra.oresing.groovy;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class BooleanGroovyExpressionTest {

    @Test
    public void testEvaluateWithTrueExpression() {
        BooleanGroovyExpression expression = BooleanGroovyExpression.forExpression("true");
        Boolean result = expression.evaluate(Collections.emptyMap());
        Assert.assertTrue(result);
    }

    @Test
    public void testEvaluateWithFalseExpression() {
        BooleanGroovyExpression expression = BooleanGroovyExpression.forExpression("false");
        Boolean result = expression.evaluate(Collections.emptyMap());
        Assert.assertFalse(result);
    }

    @Test
    public void testEvaluateWithComparisonExpression() {
        BooleanGroovyExpression expression = BooleanGroovyExpression.forExpression("1 + 1 == 2");
        Boolean result = expression.evaluate(Collections.emptyMap());
        Assert.assertTrue(result);
    }

    @Test
    public void testEvaluateWithNonBooleanThrows() {
        BooleanGroovyExpression expression = BooleanGroovyExpression.forExpression("'hello'");
        try {
            expression.evaluate(Collections.emptyMap());
            Assert.fail("une exception aurait dû être levée");
        } catch (SiOreIllegalArgumentException e) {
            Assert.assertEquals("badGroovyExpressionCheckerReturnType", e.getMessage());
        }
    }

    @Test
    public void testToString() {
        BooleanGroovyExpression expression = BooleanGroovyExpression.forExpression("true");
        Assert.assertNotNull(expression.toString());
        Assert.assertTrue(expression.toString().contains("expression"));
    }
}
