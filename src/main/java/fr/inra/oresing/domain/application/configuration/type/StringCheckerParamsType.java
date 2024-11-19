package fr.inra.oresing.domain.application.configuration.type;


import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record StringCheckerParamsType(SectionBuilder sectionBuilder,
                                      Map<String, ConfigurationSchemaNodeType> children, boolean required,
                                      boolean nullable) implements IntermediaryType.CheckerParamType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_PATTERN, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_MULTIPLICITY, EnumType.MULTIPLICITY_ENUM)
                );
    }
    public static StringCheckerParamsType  EMPTY_INSTANCE(){
        return new StringCheckerParamsType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    private StringCheckerParamsType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }
    public StringCheckerParamsType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

}
