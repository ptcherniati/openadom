package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

public sealed interface ConfigurationSchemaNodeType<T> permits FinalType, IntermediaryType {
    T children();

    String buildExample(int level);

    default StringBuilder getBuilder() {
        return new StringBuilder("%s\n".formatted(required() ? "  #mandatory" : "  #optional"));
    }

    default boolean required() {
        return false;
    }

    default boolean nullable() {
        return false;
    }

    SectionBuilder sectionBuilder();
}
