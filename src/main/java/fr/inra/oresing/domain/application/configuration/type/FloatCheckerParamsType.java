package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record FloatCheckerParamsType(SectionBuilder sectionBuilder,
                                     Map<String, ConfigurationSchemaNodeType<?>> children, boolean required,
                                     boolean nullable) implements IntermediaryType.CheckerParamType {
    public FloatCheckerParamsType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    private FloatCheckerParamsType(final Map<String, ConfigurationSchemaNodeType<?>> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_MIN, new FloatType(0F)),
                        new LabelDescription(ConfigurationSchemaNode.OA_MAX, new FloatType(0F)),
                        new LabelDescription(ConfigurationSchemaNode.OA_MULTIPLICITY, EnumType.MULTIPLICITY_ENUM));
    }

    public static FloatCheckerParamsType EMPTY_INSTANCE() {
        return new FloatCheckerParamsType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

}