package fr.inra.oresing.model.internationalization;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class InternationalizationDisplayTest {
    String pattern = "column 1 {column1} column 2 {column2} column 3 {column3} end";

    @Test
    public void getPatternColumnsTest() {
        List<String> patternColumns = InternationalizationDisplay.getPatternColumns(pattern);
        Assert.assertEquals(List.of("column1", "column2", "column3"), patternColumns);
    }

    @Test
    public void getParsePatternTest() {
        List<InternationalizationDisplay.PatternSection> patternSections = InternationalizationDisplay.parsePattern(pattern);
        for (int i = 1; i < 3; i++) {
            Assert.assertEquals((i>1?" ":"")+"column "+i+" ", patternSections.get(i-1).text);
            Assert.assertEquals("column"+i, patternSections.get(i-1).variable);
        }
        Assert.assertEquals(" end", patternSections.get(3).text);
        Assert.assertEquals("", patternSections.get(3).variable);
    }

}