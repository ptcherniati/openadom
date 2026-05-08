package fr.inra.oresing.checker;

import org.junit.Assert;
import org.junit.Test;

public class DateLineCheckerAdditionalTest {

    @Test
    public void testIsValidPatternWithValidPattern() {
        Assert.assertTrue(DateLineChecker.isValidPattern("dd/MM/yyyy"));
        Assert.assertTrue(DateLineChecker.isValidPattern("yyyy-MM-dd HH:mm:ss"));
        Assert.assertTrue(DateLineChecker.isValidPattern("MM/yyyy"));
    }

    @Test
    public void testIsValidPatternWithInvalidPattern() {
        Assert.assertFalse(DateLineChecker.isValidPattern("AAAA-BBB-CCC-invalid-pattern-!@#"));
        Assert.assertFalse(DateLineChecker.isValidPattern(""));
        Assert.assertFalse(DateLineChecker.isValidPattern("   "));
        Assert.assertFalse(DateLineChecker.isValidPattern(null));
    }

    @Test
    public void testSortableDateToFormattedDate() {
        String sortableDate = "date:2020-01-01 00:00:00:01/01/2020";
        String result = DateLineChecker.sortableDateToFormattedDate(sortableDate);
        Assert.assertEquals("01/01/2020", result);
    }

    @Test
    public void testSortableDateToFormattedDateWithNonSortableDate() {
        String normalDate = "01/01/2020";
        String result = DateLineChecker.sortableDateToFormattedDate(normalDate);
        Assert.assertEquals("01/01/2020", result);
    }

    @Test
    public void testCheckWithSortableDatePrefix() {
        DateLineChecker checker = new DateLineChecker(
                new fr.inra.oresing.model.VariableComponentKey("date", "valeur"),
                "dd/MM/yyyy", null, null);
        Assert.assertTrue(checker.check("date:2020-01-01 00:00:00:01/01/2020").isSuccess());
    }
}
