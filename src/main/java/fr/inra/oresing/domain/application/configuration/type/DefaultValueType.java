package fr.inra.oresing.domain.application.configuration.type;


import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public record DefaultValueType(SectionBuilder sectionBuilder,
                               Map<String, ConfigurationSchemaNodeType> children, boolean required,
                               boolean nullable) implements IntermediaryType.CheckerParamType {
    public static final DefaultValueType FLOAT_0 = new DefaultValueType(Map.of(
            ConfigurationSchemaNode.OA_EXPRESSION, new StringType("0")
    ), RootType.CHECKING.NO_CHECK);

    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_EXPRESSION, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_MULTIPLICITY, EnumType.MULTIPLICITY_ENUM),
                        new LabelDescription(ConfigurationSchemaNode.OA_REFERENCES, new CollectionType.ArrayType<StringType>(List.of(), true, false, StringType.EMPTY_INSTANCE()))
                );
    }
    public static DefaultValueType  EMPTY_INSTANCE() {
        return new DefaultValueType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    public DefaultValueType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    private DefaultValueType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

}
