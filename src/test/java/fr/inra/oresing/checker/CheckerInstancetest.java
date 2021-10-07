package fr.inra.oresing.checker;

import org.junit.Assert;
import org.junit.Test;

public class CheckerInstancetest {
    @Test
    public void testDateLineCheckerInstance(){
        DateLineChecker checker = new DateLineChecker(CheckerTarget.getInstance("colonne"), "DD-MM-YYYY");
        Assert.assertEquals(true, checker.instanceOf(DateLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(CheckerOnOneVariableComponentLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(LineChecker.class));
        Assert.assertEquals(false, checker.instanceOf(IntegerChecker.class));
    }
    @Test
    public void testFloatCheckerInstance(){
        FloatChecker checker = new FloatChecker(CheckerTarget.getInstance("colonne"));
        Assert.assertEquals(true, checker.instanceOf(FloatChecker.class));
        Assert.assertEquals(true, checker.instanceOf(CheckerOnOneVariableComponentLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(LineChecker.class));
        Assert.assertEquals(false, checker.instanceOf(IntegerChecker.class));
    }
    @Test
    public void testIntegerCheckerInstance(){
        IntegerChecker checker = new IntegerChecker(CheckerTarget.getInstance("colonne"));
        Assert.assertEquals(true, checker.instanceOf(IntegerChecker.class));
        Assert.assertEquals(true, checker.instanceOf(CheckerOnOneVariableComponentLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(LineChecker.class));
        Assert.assertEquals(false, checker.instanceOf(FloatChecker.class));
    }
    @Test
    public void testGroovyCheckerInstance(){
        GroovyLineChecker checker = GroovyLineChecker.forExpression("", null, null, null);
        Assert.assertEquals(true, checker.instanceOf(GroovyLineChecker.class));
        Assert.assertEquals(false, checker.instanceOf(CheckerOnOneVariableComponentLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(LineChecker.class));
        Assert.assertEquals(false, checker.instanceOf(FloatChecker.class));
    }
    @Test
    public void testReferenceCheckerInstance(){
        ReferenceLineChecker checker = new ReferenceLineChecker(CheckerTarget.getInstance("column"),null, null);
        Assert.assertEquals(true, checker.instanceOf(ReferenceLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(CheckerOnOneVariableComponentLineChecker.class));
        Assert.assertEquals(true, checker.instanceOf(LineChecker.class));
        Assert.assertEquals(false, checker.instanceOf(FloatChecker.class));
    }
}