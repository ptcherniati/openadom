package fr.inra.oresing.groovy;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class StringGroovyExpressionTest {

    @Test
    public void testEvaluateWithStringExpression() {
        StringGroovyExpression expression = StringGroovyExpression.forExpression("'bonjour'");
        String result = expression.evaluate(Collections.emptyMap());
        Assert.assertEquals("bonjour", result);
    }

    @Test
    public void testEvaluateWithNumberExpression() {
        StringGroovyExpression expression = StringGroovyExpression.forExpression("42");
        String result = expression.evaluate(Collections.emptyMap());
        Assert.assertEquals("42", result);
    }

    @Test
    public void testEvaluateWithNonStringOrNumberThrows() {
        StringGroovyExpression expression = StringGroovyExpression.forExpression("true");
        try {
            expression.evaluate(Collections.emptyMap());
            Assert.fail("une exception aurait dû être levée");
        } catch (SiOreIllegalArgumentException e) {
            Assert.assertEquals("badGroovyExpressionCheckerReturnType", e.getMessage());
        }
    }

    @Test
    public void testToString() {
        StringGroovyExpression expression = StringGroovyExpression.forExpression("'test'");
        Assert.assertNotNull(expression.toString());
        Assert.assertTrue(expression.toString().contains("expression"));
    }
}
