package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public record PatternComponentQualifierType(SectionBuilder sectionBuilder,
                                            Map<String, ConfigurationSchemaNodeType> children,
                                            boolean required,
                                            boolean nullable) implements ApplicationType.ComponentType {

    public static PatternComponentQualifierType EMPTY_INSTANCE(){
        return new PatternComponentQualifierType(Map.of(), RootType.CHECKING.NO_CHECK);
    }
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(List.of(), false, true, StringType.EMPTY_INSTANCE())),
                        new LabelDescription(ConfigurationSchemaNode.OA_EXPORT_HEADER, TitleType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_CHECKER, CheckerType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(false)),
                        new LabelDescription(ConfigurationSchemaNode.OA_DEFAULT_VALUE, DefaultValueType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, new CollectionType.ArrayType<>(List.of(), false, true, StringType.EMPTY_INSTANCE()))
                );
    }

    private PatternComponentQualifierType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public PatternComponentQualifierType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }
}
