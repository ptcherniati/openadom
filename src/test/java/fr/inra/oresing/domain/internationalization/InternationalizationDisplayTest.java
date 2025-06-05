package fr.inra.oresing.domain.internationalization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@org.junit.jupiter.api.Tag("domain.i18n")
public class InternationalizationDisplayTest {
    final String pattern = "column 1 {column1} column 2 {column2} column 3 {column3} end";

    @Test
    void getPatternColumnsTest() {
        final List<String> patternColumns = InternationalizationDisplay.getPatternColumns(pattern);
        assertEquals(List.of("column1", "column2", "column3"), patternColumns);
    }

    @Test
    void getParsePatternTest() {
        final List<InternationalizationDisplay.PatternSection> patternSections = InternationalizationDisplay.parsePattern(pattern);
        for (int i = 1; i < 3; i++) {
            assertEquals((i > 1 ? " " : "") + "column " + i + " ", patternSections.get(i - 1).text);
            assertEquals("column" + i, patternSections.get(i - 1).variable);
        }
        assertEquals(" end", patternSections.get(3).text);
        assertEquals("", patternSections.get(3).variable);
    }

}