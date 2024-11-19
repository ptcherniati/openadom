package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

public sealed interface FinalType<T> extends ConfigurationSchemaNodeType<T> permits BooleanType, EnumType, FloatType, IntegerType, StringType {

    default String buildExample(final int level) {
        return children().toString() + "%s\n".formatted(required() ? "  #mandatory" : "  #optional");
    }

    @Override
    default SectionBuilder sectionBuilder() {
        return null;
    }

}
