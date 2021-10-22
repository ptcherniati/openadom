package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import org.junit.Assert;
import org.junit.Test;


public class DateLineCheckerTest {

    @Test
    public void testCheck() {
        DateLineChecker dateLineChecker = new DateLineChecker(CheckerTarget.getInstance(new VariableComponentKey("ignored", "ignored")), "dd/MM/yyyy", null);
        Assert.assertTrue(dateLineChecker.check("12/01/2021").isSuccess());
        Assert.assertFalse(dateLineChecker.check("06/21").isSuccess());
        Assert.assertFalse(dateLineChecker.check("04/03/10").isSuccess());
    }
}