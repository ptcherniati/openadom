package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.StringType;
import fr.inra.oresing.domain.application.configuration.type.TagType;

import java.util.LinkedHashMap;
import java.util.List;

class TagExampleBuilder {
    protected static CollectionType.MapType<TagType> buildTagSchema() {
        LinkedHashMap<String, TagType> map = new LinkedHashMap<>();
        map.put("data", new TagType(I18nExampleBuilder.buildI18n("données", "data")));
        map.put("context", new TagType(I18nExampleBuilder.buildI18n("contexte", "context")));

        return new CollectionType.MapType<>(map, false, true, TagType.EMPTY_INSTANCE());
    }


    protected static List<StringType> buildTagArray(final List<String> tags) {
        return tags.stream().map(StringType::new).toList();
    }
}