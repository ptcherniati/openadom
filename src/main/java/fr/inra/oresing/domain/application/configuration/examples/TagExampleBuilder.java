package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.StringType;
import fr.inra.oresing.domain.application.configuration.type.TagType;

import java.util.LinkedHashMap;
import java.util.List;

class TagExampleBuilder {
    protected static CollectionType.MapType<TagType> buildTagSchema() {
        return new CollectionType.MapType<TagType>(new LinkedHashMap<String, TagType>() {{
            put("data", new TagType(I18nExampleBuilder.buildI18n("données", "data")));
            put("context", new TagType(I18nExampleBuilder.buildI18n("contexte", "context")));
        }},
                false, true, TagType.EMPTY_INSTANCE());
    }

    protected static List<StringType> buildTagArray(final List<String> tags) {
        return tags.stream().map(StringType::new).toList();
    }
}