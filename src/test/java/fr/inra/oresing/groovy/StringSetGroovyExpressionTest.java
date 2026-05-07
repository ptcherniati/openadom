package fr.inra.oresing.groovy;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class StringSetGroovyExpressionTest {

    @Test
    public void testEvaluateWithString() {
        StringSetGroovyExpression expression = StringSetGroovyExpression.forExpression("'alpha'");

        Set<String> result = expression.evaluate(Collections.emptyMap());

        Assert.assertEquals(Set.of("alpha"), result);
    }

    @Test
    public void testEvaluateWithIterableOfString() {
        StringSetGroovyExpression expression = StringSetGroovyExpression.forExpression("['alpha', 'beta']");

        Set<String> result = expression.evaluate(Collections.emptyMap());

        Assert.assertEquals(new LinkedHashSet<>(Set.of("alpha", "beta")), result);
    }

    @Test
    public void testEvaluateWithIterableOfNumber() {
        StringSetGroovyExpression expression = StringSetGroovyExpression.forExpression("[1, 2, 3]");

        Set<String> result = expression.evaluate(Collections.emptyMap());

        Assert.assertEquals(new LinkedHashSet<>(Set.of("1", "2", "3")), result);
    }

    @Test
    public void testEvaluateWithNull() {
        StringSetGroovyExpression expression = StringSetGroovyExpression.forExpression("null");

        Set<String> result = expression.evaluate(Collections.emptyMap());

        Assert.assertNull(result);
    }

    @Test
    public void testEvaluateWithUnsupportedIterableType() {
        StringSetGroovyExpression expression = StringSetGroovyExpression.forExpression("[true]");

        try {
            expression.evaluate(Collections.emptyMap());
            Assert.fail("une exception aurait dû être levée");
        } catch (SiOreIllegalArgumentException e) {
            Assert.assertEquals("badGroovyExpressionCheckerReturnType", e.getMessage());
        }
    }
}
