package fr.inra.oresing.rest.model.configuration.builder;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record I18n(Map i18n) {
    public static final Map<String, String> titles = ImmutableMap.of(
            ConfigurationSchemaNode.OA_TITLE, InternationalizationTitle.TITLE,
            ConfigurationSchemaNode.OA_DESCRIPTION , InternationalizationTitle.DESCRIPTION
    );
    public I18n add(final String path, final Map i18n) {
        if(MapUtils.isEmpty(i18n)){
            return this;
        }
        Set<String> titleI18nKeys = titles.keySet();
        if(titleI18nKeys.stream()
                .anyMatch(i18n.keySet()::contains)
        ){
            final Map<String, Map> localizations = i18n();
            for (String titleI18nKey : titleI18nKeys) {
                Map internationalizationForTitleI18nKey = (Map) i18n.get(titleI18nKey);
                if(MapUtils.isEmpty(internationalizationForTitleI18nKey)) {continue;}
                String pathForTitleI18nKey = String.join(".", path, titles.get(titleI18nKey));
                localizations.putAll(add(pathForTitleI18nKey, internationalizationForTitleI18nKey).i18n());
            }
            return new I18n(localizations);
        }
        final String[] labels = path.split("\\.");
        final Map<String, Map> localizations = i18n();
        Map<String, Map> currentLocalization = localizations;
        for (int i = 0; i < labels.length - 1; i++) {

            currentLocalization = currentLocalization.computeIfAbsent(labels[i], a -> new HashMap<>());
        }
        if (i18n != null) {
            for (final Object key : i18n.keySet()) {
                final Locale locale = new Locale(key.toString());
                if (StringUtils.isAllLowerCase(key.toString()) &&
                        (key.toString().length() != 2)) {
                    locale.getUnicodeLocaleType(key.toString());
                }
            }
            currentLocalization.put(
                    labels[labels.length - 1],
                    (Map) i18n.keySet().stream()
                            .filter(i18n::containsKey)
                            .filter(k -> !Strings.isNullOrEmpty((String) i18n.get(k)))
                            .collect(Collectors.toMap(Function.identity(), i18n::get)));
        }
        return new I18n(localizations);
    }
}
