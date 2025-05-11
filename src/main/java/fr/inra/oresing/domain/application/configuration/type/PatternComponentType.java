package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public record PatternComponentType(SectionBuilder sectionBuilder,
                                   Map<String, ConfigurationSchemaNodeType> children,
                                   boolean required,
                                   boolean nullable) implements ApplicationType.ComponentType {

    public static PatternComponentType  EMPTY_INSTANCE(){
        return new PatternComponentType(Map.of(), RootType.CHECKING.NO_CHECK);
    }
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_PATTERN_FOR_COMPONENTS, new StringType((""))))
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPONENT_QUALIFIERS, StaticMapType.PATTERN_COMPONENTS_QUALIFIERS().type),
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS, StaticMapType.PATTERN_COMPONENTS_ADJACENT().type),
                        new LabelDescription(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(List.of(), false, true, StringType.EMPTY_INSTANCE())),
                        new LabelDescription(ConfigurationSchemaNode.OA_EXPORT_HEADER, TitleType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(false)),
                        new LabelDescription(ConfigurationSchemaNode.OA_CHECKER, CheckerType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DEFAULT_VALUE, DefaultValueType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, new CollectionType.ArrayType<>(List.of(), false, true, StringType.EMPTY_INSTANCE()))
                );
    }

    private PatternComponentType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public PatternComponentType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

}