package fr.inra.oresing.domain.application.configuration.type;


import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record IntegerCheckerParamsType(SectionBuilder sectionBuilder,
                                       Map<String, ConfigurationSchemaNodeType<?>> children, boolean required,
                                       boolean nullable) implements IntermediaryType.CheckerParamType {
    private IntegerCheckerParamsType(final Map<String, ConfigurationSchemaNodeType<?>> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public IntegerCheckerParamsType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_MIN, new IntegerType(0)),
                        new LabelDescription(ConfigurationSchemaNode.OA_MAX, new IntegerType(0)),
                        new LabelDescription(ConfigurationSchemaNode.OA_MULTIPLICITY, EnumType.MULTIPLICITY_ENUM)
                );
    }

    public static IntegerCheckerParamsType EMPTY_INSTANCE() {
        return new IntegerCheckerParamsType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

}