package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.I18nType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class I18nExampleBuilder {
    protected static CollectionType.ArrayType<StringType> LANG_RESTRICTION_FR = new CollectionType.ArrayType<>(
            List.of(StringType.FR),
            false,
            false,
            StringType.EMPTY_INSTANCE()
    );
    protected static CollectionType.ArrayType<StringType> LANG_RESTRICTION_EN = new CollectionType.ArrayType<>(
            List.of( StringType.EN),
            false,
            false,
            StringType.EMPTY_INSTANCE()
    );
    protected static CollectionType.ArrayType<StringType> LANG_RESTRICTION_FR_EN = new CollectionType.ArrayType<>(
            List.of( StringType.FR, StringType.EN),
            false,
            false,
            StringType.EMPTY_INSTANCE()
    );

    protected static I18nType buildI18n(final String fr, final String en) {
        return new I18nType(
                new LinkedHashMap<String, String>() {{
                    if (fr != null) {
                        put("fr", fr);
                    }
                    if (en != null) {
                        put("en", en);
                    }
                }});
    }

    protected static CollectionType.MapType<I18nType> buildI18nDisplay(final I18nType i18nDisplay) {
        return new CollectionType.MapType<I18nType>(Map.of(ConfigurationSchemaNode.OA_PATTERN, i18nDisplay), true, false, I18nType.EMPTY_INSTANCE());
    }
}