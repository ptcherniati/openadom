package fr.inra.oresing.domain.internationalization;

import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
//import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import com.google.common.base.Strings;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
public class InternationalizationDisplay {
    //@ApiModelProperty(notes = "pattern in differents locales, used to display a reference when referred to",required = false)
    Map<Locale, String> pattern;

    public static DataDatum getDisplaysName(final DataImporterContext dataImporterContext, final DataDatum refValues) {
        Optional<InternationalizationTitle> displayPattern = dataImporterContext.getDisplayPattern();
        final DataDatum displaysName = new DataDatum();
        displayPattern
                .ifPresent(patterns -> {
                    patterns.getTitle().entrySet()
                            .forEach(stringEntry -> {
                                displaysName.put(DataColumn.forDisplayName(stringEntry.getKey()),
                                        new DataColumnSingleValue(
                                                StringType.getStringTypeFromStringValue(
                                                parsePattern(stringEntry.getValue()).stream()
                                                .map(patternSection -> {
                                                            String internationalizedPattern = patternSection.text;
                                                            if (!Strings.isNullOrEmpty(patternSection.variable)) {
                                                                String referencedColumn = patternSection.variable;
                                                                internationalizedPattern += refValues.get(new DataColumn(referencedColumn)).toValueString(dataImporterContext, referencedColumn, stringEntry.getKey().getDisplayName());                                                            }
                                                            return internationalizedPattern;
                                                        }
                                                )
                                                .collect(Collectors.joining()))
                                        )
                                );
                            });
                });
        String defaultDisplay = dataImporterContext.getNaturalKeyColumns()
                .stream()
                .map(columnName ->
                        refValues.values().entrySet()
                                .stream()
                                .filter(entry -> entry.getKey().column().equals(columnName))
                                .map(Map.Entry::getValue)
                                .map(DataColumnValue::toJsonForFrontend)
                                .map(Object::toString)
                                .findFirst()
                                .orElse("")

                )
                .collect(Collectors.joining(DataImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));

        displaysName.put(DataColumn.forDisplayName("default"), new DataColumnSingleValue(StringType.getStringTypeFromStringValue(defaultDisplay)));
        return displaysName;
    }

    public static DataDatum getDisplaysDescription(final DataImporterContext dataImporterContext, final DataDatum refValues) {
        Optional<InternationalizationTitle> displayPattern = dataImporterContext.getDisplayPattern();
        final String refType = dataImporterContext.getRefType();
        final DataDatum displaysDescription = new DataDatum();
        displayPattern
                .ifPresent(patterns -> {
                    patterns.getDescription().entrySet()
                            .forEach(stringEntry -> {
                                displaysDescription.put(DataColumn.forDisplayDescription(stringEntry.getKey()),
                                        new DataColumnSingleValue(
                                                StringType.getStringTypeFromStringValue(
                                                parsePattern(stringEntry.getValue()).stream()
                                                .map(patternSection -> {
                                                            String internationalizedPattern = patternSection.text;
                                                            if (!Strings.isNullOrEmpty(patternSection.variable)) {
                                                                String referencedColumn = patternSection.variable;
                                                                internationalizedPattern += refValues.get(new DataColumn(referencedColumn)).toValueString(dataImporterContext, referencedColumn, stringEntry.getKey().getDisplayName());                                                            }
                                                            return internationalizedPattern;
                                                        }
                                                )
                                                .collect(Collectors.joining()))
                                        )
                                );
                            });
                });
        String defaultDisplay = dataImporterContext.getNaturalKeyColumns()
                .stream()
                .map(columnName ->
                        refValues.values().entrySet()
                                .stream()
                                .filter(entry -> entry.getKey().column().equals(columnName))
                                .map(Map.Entry::getValue)
                                .map(DataColumnValue::toJsonForFrontend)
                                .map(Object::toString)
                                .findFirst()
                                .orElse("")

                )
                .collect(Collectors.joining(DataImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
        return displaysDescription;
    }

    public static List<String> getPatternColumns(final String pattern) {
        return getPatternSplitStream(pattern)
                .map(k -> k.length > 1 ? k[1] : "")
                .filter(k-> !Strings.isNullOrEmpty(k))
                .collect(Collectors.toList());
    }

    public static List<PatternSection> parsePattern(final String pattern) {
        return getPatternSplitStream(pattern)
                .map(section->new PatternSection(section))
                .collect(Collectors.toList());
    }

    private static Stream<String[]> getPatternSplitStream(final String pattern) {
        return Stream.of(pattern.split("}"))
                            .map(s -> s.split("\\{"));
    }

    public static class PatternSection{
        final String text;
        final String variable;

        public PatternSection(final String[] section) {
            super();
            text = section[0];
            variable = section.length > 1 ? section[1] : "";
        }
    }
}