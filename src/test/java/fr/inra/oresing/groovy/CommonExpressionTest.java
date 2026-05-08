package fr.inra.oresing.groovy;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class CommonExpressionTest {

    @Test
    public void testEmptyStringEvaluatesToEmptyString() {
        String result = CommonExpression.EMPTY_STRING.evaluate(Collections.emptyMap());
        Assert.assertEquals("", result);
    }

    @Test
    public void testEmptyStringEvaluatesToEmptyStringWithNonEmptyContext() {
        java.util.Map<String, Object> context = java.util.Map.of("key", "value");
        String result = CommonExpression.EMPTY_STRING.evaluate(context);
        Assert.assertEquals("", result);
    }

    @Test
    public void testToStringContainsDescription() {
        String description = CommonExpression.EMPTY_STRING.toString();
        Assert.assertNotNull(description);
        Assert.assertTrue(description.contains("chaîne"));
    }
}
