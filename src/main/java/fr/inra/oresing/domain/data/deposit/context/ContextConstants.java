package fr.inra.oresing.domain.data.deposit.context;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.data.deposit.context.hierarchicalkey.HierarchicalKeyFactory;
import fr.inra.oresing.domain.internationalization.InternationalizationDisplay;

import java.util.*;
import java.util.stream.Collectors;

public record ContextConstants(
        Application application,
        String refType,
        InternationalizationData internationalizationReferenceMap,
        InternationalizationTitle displayPattern,
        HierarchicalKeyFactory hierarchicalKeyFactory,
        Map<Locale, List<InternationalizationDisplay.PatternSection>> patternSection,
        StandardDataDescription dataConfiguration
) {

    public static ContextConstants with(Application application, final String refType) {
        final StandardDataDescription dataDescription = application.getConfiguration().dataDescription().get(refType);
        final InternationalizationData internationalizationData = buildInternationalizationReferenceMap(application.getConfiguration(), refType).orElse(null);
        final Optional<InternationalizationTitle> displayPattern = buildDisplayPattern(internationalizationData);
        return new ContextConstants(
                application,
                refType,
                internationalizationData,
                displayPattern.orElse(null),
                buildHierarchicalKeyFactory(application, refType),
                displayPattern.map(ContextConstants::buildPatternSection).orElse(null),
                dataDescription
        );
    }


    static Optional<InternationalizationData> buildInternationalizationReferenceMap(final Configuration conf, final String refType) {
        return Optional.ofNullable(conf)
                .map(Configuration::i18n)
                .map(Internationalizations::getData)
                .map(references -> references.getOrDefault(refType, null));
    }

    static Optional<InternationalizationTitle> buildDisplayPattern(final InternationalizationData internationalizationData) {
        return Optional.ofNullable(internationalizationData)
                .map(InternationalizationData::getI18nDisplayPattern);
    }

    static HierarchicalKeyFactory buildHierarchicalKeyFactory(final Application application, final String refType) {
        return HierarchicalKeyFactory.build(application, refType);
    }

    static Map<Locale, List<InternationalizationDisplay.PatternSection>> buildPatternSection(final InternationalizationTitle displayPattern) {
        return Optional.ofNullable(displayPattern)
                .map(InternationalizationTitle::getTitle)
                .map(dp -> dp.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, k -> InternationalizationDisplay.parsePattern(k.getValue())))
                ).orElse(null);
    }

    public record InternationalizationReferenceMap(
            Map<String, String> internationalizationName,
            Map<String, Map<String, String>> internationalizedColumns,
            Map<String, Map<String, String>> internationalizedDynamicColumns,
            InternationalizationDisplay internationalizationDisplay,
            Map<String, Map<String, String>> internationalizedValidations,
            Map<String, Map<String, String>> internationalizedTags
    ) {
    }

    public record DataInternationalizations(
            LinkedHashMap<Locale, DataInternationalization> dataInternationalizations) {
    }

    record DataInternationalization(
            DataInternationalization i18nColumns,
            Map<String, String> i18nDisplay,
            Map<String, String> i18n
    ) {
    }

    public record InternationalizationWithDescription(
            Map<String, String> description_fr,
            Map<String, String> nom_key
    ) {

    }
}