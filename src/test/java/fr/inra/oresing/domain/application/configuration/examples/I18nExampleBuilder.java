package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.I18nType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class I18nExampleBuilder {
    protected static final CollectionType.ArrayType<StringType> LANG_RESTRICTION_FR = new CollectionType.ArrayType<>(
            List.of(StringType.FR),
            false,
            false,
            StringType.EMPTY_INSTANCE()
    );
    protected static final CollectionType.ArrayType<StringType> LANG_RESTRICTION_EN = new CollectionType.ArrayType<>(
            List.of(StringType.EN),
            false,
            false,
            StringType.EMPTY_INSTANCE()
    );
    protected static final CollectionType.ArrayType<StringType> LANG_RESTRICTION_FR_EN = new CollectionType.ArrayType<>(
            List.of(StringType.FR, StringType.EN),
            false,
            false,
            StringType.EMPTY_INSTANCE()
    );

    protected static I18nType buildI18n(final String fr, final String en) {
        LinkedHashMap<String, String> children = new LinkedHashMap<>();
        if (fr != null) {
            children.put("fr", fr);
        }
        if (en != null) {
            children.put("en", en);
        }

        return new I18nType(
                children);
    }

    protected static CollectionType.MapType<I18nType> buildI18nDisplay(final I18nType i18nDisplay) {
        return new CollectionType.MapType<>(Map.of(ConfigurationSchemaNode.OA_PATTERN, i18nDisplay), true, false, I18nType.EMPTY_INSTANCE());
    }
}