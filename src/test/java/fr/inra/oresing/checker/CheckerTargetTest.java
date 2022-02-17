package fr.inra.oresing.checker;

import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
import org.junit.Assert;
import org.junit.Test;

public class CheckerTargetTest {
    @Test
    public void testBuildColumnChecker(){
        ReferenceColumn referenceColumn = new ReferenceColumn("bonjour");
        CheckerTarget checkerTarget= CheckerTarget.getInstance(referenceColumn,null, null);
        Assert.assertEquals(CheckerTarget.CheckerTargetType.PARAM_COLUMN, checkerTarget.getType());
        Assert.assertEquals(referenceColumn, checkerTarget.getTarget());
        String key = checkerTarget.getInternationalizedKey("key");
        Assert.assertEquals("keyWithColumn", key);
    }
    @Test
    public void testBuildVariableComponentChecker(){
        VariableComponentKey variableComponentKey = new VariableComponentKey("Variable", "component");
        CheckerTarget checkerTarget= CheckerTarget.getInstance( variableComponentKey, null, null);
        Assert.assertEquals(CheckerTarget.CheckerTargetType.PARAM_VARIABLE_COMPONENT_KEY, checkerTarget.getType());
        Assert.assertEquals(variableComponentKey, checkerTarget.getTarget());
        String key = checkerTarget.getInternationalizedKey("key");
        Assert.assertEquals("key", key);
    }
}