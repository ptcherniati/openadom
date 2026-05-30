package fr.inra.oresing.domain.internationalization;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
public class InternationalizationDisplay {
    //@ApiModelProperty(notes = "pattern in differents locales, used to display a reference when referred to",required = false)
    Map<Locale, String> pattern;

    public static DataDatum getDisplaysName(final AsynchroneFileImporterContext dataImporterContext, final DataDatum refValues) {
        Optional<InternationalizationTitle> displayPattern = dataImporterContext.getDisplayPattern();
        final DataDatum displaysName = new DataDatum();
        Locale defaultLanguage = Optional.ofNullable(dataImporterContext.contextConstants().application())
                .map(Application::getConfiguration)
                .map(Configuration::applicationDescription)
                .map(ApplicationDescription::defaultLanguage)
                .orElse(Locale.FRENCH);
        displayPattern
                .ifPresent(patterns -> patterns.getTitle()
                        .forEach((key, value) -> {
                            DataColumnSingleValue displayForLocale = buildDisplayForLocale(value, key, refValues, dataImporterContext);
                            displaysName.put(DataColumn.forDisplayName(key),
                                    displayForLocale
                            );
                            if (key.equals(defaultLanguage)) {
                                displaysName.put(DataColumn.forDisplayName(DataColumn.DEFAULT), displayForLocale);
                            }
                        }));
        if (!displaysName.contains(DataColumn.forDisplayName(DataColumn.DEFAULT)) ||
            Strings.isNullOrEmpty(displaysName.get(DataColumn.forDisplayName(DataColumn.DEFAULT)).toJsonForFrontend().toString())) {
            String defaultDisplay = dataImporterContext.getNaturalKeyColumns()
                    .stream()
                    .map(columnName -> lookupDisplayValue(refValues, columnName))
                    .collect(Collectors.joining(AsynchroneFileImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));

            displaysName.put(DataColumn.forDisplayName(DataColumn.DEFAULT), new DataColumnSingleValue(
                            StringType.getStringTypeFromStringValue(defaultDisplay)
                    )
            );

        }
        return displaysName;
    }

    public static DataDatum getDisplaysDescription(final AsynchroneFileImporterContext dataImporterContext, final DataDatum refValues) {
        Optional<InternationalizationTitle> displayPattern = dataImporterContext.getDisplayPattern();
        final DataDatum displaysDescription = new DataDatum();
        Locale defaultLanguage = Optional.ofNullable(dataImporterContext.contextConstants().application())
                .map(Application::getConfiguration)
                .map(Configuration::applicationDescription)
                .map(ApplicationDescription::defaultLanguage)
                .orElse(Locale.FRENCH);
        displayPattern
                .ifPresent(patterns -> patterns.getDescription().forEach((key, value) -> {
                    DataColumnSingleValue displayForLocale = buildDisplayForLocale(value, key, refValues, dataImporterContext);
                    displaysDescription.put(DataColumn.forDisplayDescription(key),
                            displayForLocale
                    );
                    if (key.equals(defaultLanguage)) {
                        displaysDescription.put(DataColumn.forDisplayDescription(DataColumn.DEFAULT), displayForLocale);
                    }
                }));
        if (!displaysDescription.contains(DataColumn.forDisplayName(DataColumn.DEFAULT)) ||
            Strings.isNullOrEmpty(displaysDescription.get(DataColumn.forDisplayName(DataColumn.DEFAULT)).toJsonForFrontend().toString())) {
            String defaultDisplay = dataImporterContext.getNaturalKeyColumns()
                    .stream()
                    .map(columnName -> lookupDisplayValue(refValues, columnName))
                    .collect(Collectors.joining(AsynchroneFileImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
            displaysDescription.put(DataColumn.forDisplayDescription(DataColumn.DEFAULT), new DataColumnSingleValue(
                            StringType.getStringTypeFromStringValue(
                                    defaultDisplay
                            )
                    )
            );

        }
        return displaysDescription;
    }

    /**
     * Recupere la valeur frontend d'une colonne du datum par lookup direct O(1)
     * sur la map, au lieu d'un scan lineaire filter+findFirst des entrees. La cle
     * {@link DataColumn} est un record a composant unique : son egalite est
     * l'egalite de la chaine {@code column}, donc une map ne peut contenir deux
     * cles equivalentes. Le lookup retourne donc exactement la meme entree que
     * l'ancien {@code filter(key.column().equals(columnName)).findFirst()}
     * ( iso-resultat ). Colonne absente -&gt; chaine vide ( comme l'ancien
     * {@code orElse("")} ).
     */
    private static String lookupDisplayValue(final DataDatum refValues, final String columnName) {
        DataColumnValue value = refValues.values().get(new DataColumn(columnName));
        return value == null ? "" : value.toJsonForFrontend().toString();
    }

    /**
     * TRANSFORM iter2 #2 : memoize parsePattern result per pattern string .
     * Profile async-profiler shows {@code Pattern.compile} = 260 samples ( 13% CPU )
     * on hot transform path . buildDisplayForLocale calls parsePattern per row x
     * per locale x ( name + description ) , causing N x split("}") + split("\\{")
     * regex operations . Pattern strings come from app config = bounded ( a few
     * dozen per application ) , safe to keep as static cache ( app-scoped values ,
     * never user-input at runtime ) . Use immutable lists for safe sharing .
     */
    private static final ConcurrentHashMap<String, List<PatternSection>> PARSE_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> COLUMNS_CACHE =
            new ConcurrentHashMap<>();

    public static List<String> getPatternColumns(final String pattern) {
        return COLUMNS_CACHE.computeIfAbsent(pattern, p ->
                getPatternSplitStream(p)
                        .map(k -> k.length > 1 ? k[1] : "")
                        .filter(k -> !Strings.isNullOrEmpty(k))
                        .toList());
    }

    public static List<PatternSection> parsePattern(final String pattern) {
        return PARSE_CACHE.computeIfAbsent(pattern, p ->
                getPatternSplitStream(p)
                        .map(PatternSection::new)
                        .toList());
    }

    private static Stream<String[]> getPatternSplitStream(final String pattern) {
        return Stream.of(pattern.split("}"))
                .map(s -> s.split("\\{"));
    }

    public static class PatternSection {
        final String text;
        final String variable;

        public PatternSection(final String[] section) {
            super();
            text = section[0];
            variable = section.length > 1 ? section[1] : "";
        }
    }

    private static DataColumnSingleValue buildDisplayForLocale(String value, Locale key, DataDatum refValues, AsynchroneFileImporterContext dataImporterContext) {
        return new DataColumnSingleValue(
                StringType.getStringTypeFromStringValue(
                        parsePattern(value).stream()
                                .map(patternSection -> {
                                    String internationalizedPattern = patternSection.text;
                                    if (!Strings.isNullOrEmpty(patternSection.variable)) {
                                        String referencedColumn = patternSection.variable;
                                        internationalizedPattern += refValues.get(new DataColumn(referencedColumn)).toValueString(dataImporterContext, referencedColumn, key.getDisplayName());
                                    }
                                    return internationalizedPattern;
                                })
                                .collect(Collectors.joining())
                )
        );
    }
}