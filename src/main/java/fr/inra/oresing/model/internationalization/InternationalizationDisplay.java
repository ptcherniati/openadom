package fr.inra.oresing.model.internationalization;

import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceDatum;
import lombok.Getter;
import lombok.Setter;
import org.assertj.core.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
public class InternationalizationDisplay {
    Map<String, String> pattern;

    public static ReferenceDatum getDisplays(Optional<Map<String, String>> displayPattern, Map<String, Internationalization> displayColumns, ReferenceDatum refValues) {
        ReferenceDatum displays = new ReferenceDatum();
        displayPattern
                .ifPresent(patterns -> {
                    patterns.entrySet().stream()
                            .forEach(stringEntry -> {
                                displays.put(new ReferenceColumn("__display_" + stringEntry.getKey()),
                                        parsePattern(stringEntry.getValue()).stream()
                                                .map(patternSection -> {
                                                            String internationalizedPattern = patternSection.text;
                                                            if (!Strings.isNullOrEmpty(patternSection.variable)) {
                                                                String referencedColumn = patternSection.variable;
                                                                if (displayColumns.containsKey(referencedColumn)) {
                                                                    referencedColumn = displayColumns.get(referencedColumn).getOrDefault(stringEntry.getKey(), referencedColumn);
                                                                }
                                                                internationalizedPattern += refValues.get(new ReferenceColumn(referencedColumn));
                                                            }
                                                            return internationalizedPattern;
                                                        }
                                                )
                                                .collect(Collectors.joining())
                                );
                            });
                });
        return displays;
    }

    public static List<String> getPatternColumns(String pattern) {
        return getPatternSplitStream(pattern)
                .map(k -> k.length > 1 ? k[1] : "")
                .filter(k-> !Strings.isNullOrEmpty(k))
                .collect(Collectors.toList());
    }

    public static List<PatternSection> parsePattern(String pattern) {
        return getPatternSplitStream(pattern)
                .map(section->new PatternSection(section))
                .collect(Collectors.toList());
    }

    private static Stream<String[]> getPatternSplitStream(String pattern) {
        return Stream.of(pattern.split("}"))
                            .map(s -> s.split("\\{"));
    }

    public static class PatternSection{
        String text;
        String variable;

        public PatternSection(String[] section) {
            this.text= section[0];
            this.variable=section.length > 1 ? section[1] : "";
        }
    }
}