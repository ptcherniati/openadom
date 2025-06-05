package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public sealed interface CollectionType<T, C extends ConfigurationSchemaNodeType<?>>
        extends IntermediaryType<T>
        permits CollectionType.ArrayType, CollectionType.MapType {
    C type();

    record MapType<C extends ConfigurationSchemaNodeType<?>>(
            Map<String, C> children, boolean required,
            boolean nullable,
            C type)
            implements CollectionType<Map<String, C>, C> {

        public static MapType<PatternComponentQualifierType> PATTERN_COMPONENT_QUALIFIER_EMPTY_INSTANCE() {
            return new MapType<>(Map.of(), false, false, PatternComponentQualifierType.EMPTY_INSTANCE());
        }

        public static MapType<PatternComponentAdjacentType> PATTERN_COMPONENT_ADJACENT_EMPTY_INSTANCE() {
            return new MapType<>(Map.of(), false, false, PatternComponentAdjacentType.EMPTY_INSTANCE());
        }


        @Override
        public SectionBuilder sectionBuilder() {
            return SectionBuilder.ofAny();
        }

        @Override
        public String buildExample(final int level) {
            final StringBuilder builder = getBuilder();
            for (final Map.Entry<String, C> entry : children.entrySet()) {
                final String label = entry.getKey();
                final C value = entry.getValue();
                String exampleValue = value.buildExample(level + 1);
                exampleValue = Strings.isNullOrEmpty(exampleValue) ? "\n" : exampleValue;
                builder.append("%1$s%2$s: %3$s".formatted(Strings.repeat("  ", level), label, exampleValue));
            }
            return builder.toString();
        }
    }

    record ArrayType<C extends ConfigurationSchemaNodeType<?>>(
            List<C> children,
            boolean required,
            boolean nullable,
            C type
    ) implements CollectionType<List<C>, C> {

        public static ArrayType<PatternComponentType> PATTERN_COMPONENT_EMPTY_INSTANCE() {
            return new ArrayType<>(List.of(), false, false, PatternComponentType.EMPTY_INSTANCE());
        }

        @Override
        public String buildExample(final int level) {
            final StringBuilder builder = getBuilder();
            for (final C child : children) {
                builder.append("%1$s- %2$s".formatted(Strings.repeat("  ", level), child.buildExample(level + 1)));
            }
            return builder.toString();
        }

        @Override
        public SectionBuilder sectionBuilder() {
            return SectionBuilder.ofAny();
        }
    }
}