package fr.inra.oresing.model;

import org.junit.Assert;
import org.junit.Test;

public class VariableComponentKeyTest {

    @Test
    public void testGetId() {
        VariableComponentKey key = new VariableComponentKey("temperature", "valeur");
        Assert.assertEquals("temperature_valeur", key.getId());
    }

    @Test
    public void testParseId() {
        VariableComponentKey key = VariableComponentKey.parseId("temperature_valeur");
        Assert.assertEquals("temperature", key.getVariable());
        Assert.assertEquals("valeur", key.getComponent());
    }

    @Test
    public void testParseIdRoundTrip() {
        VariableComponentKey original = new VariableComponentKey("mesure", "unite");
        VariableComponentKey parsed = VariableComponentKey.parseId(original.getId());
        Assert.assertEquals(original, parsed);
    }

    @Test
    public void testToHumanReadableString() {
        VariableComponentKey key = new VariableComponentKey("pluie", "cumul");
        Assert.assertEquals("pluie/cumul", key.toHumanReadableString());
    }

    @Test
    public void testGetInternationalizedKey() {
        VariableComponentKey key = new VariableComponentKey("temperature", "valeur");
        Assert.assertEquals("myKey", key.getInternationalizedKey("myKey"));
    }

    @Test
    public void testGetType() {
        VariableComponentKey key = new VariableComponentKey("x", "y");
        Assert.assertEquals(fr.inra.oresing.checker.CheckerTarget.CheckerTargetType.PARAM_VARIABLE_COMPONENT_KEY, key.getType());
    }

    @Test
    public void testToSqlExtractPattern() {
        VariableComponentKey key = new VariableComponentKey("temperature", "valeur");
        String pattern = key.toSqlExtractPattern();
        Assert.assertTrue(pattern.contains("temperature"));
        Assert.assertTrue(pattern.contains("valeur"));
        Assert.assertTrue(pattern.contains("aggreg.datavalues"));
    }

    @Test
    public void testEquality() {
        VariableComponentKey key1 = new VariableComponentKey("a", "b");
        VariableComponentKey key2 = new VariableComponentKey("a", "b");
        VariableComponentKey key3 = new VariableComponentKey("a", "c");
        Assert.assertEquals(key1, key2);
        Assert.assertNotEquals(key1, key3);
    }
}
