package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record ReferenceCheckerParamsType(
        SectionBuilder sectionBuilder,
        Map<String, ConfigurationSchemaNodeType> children, boolean required,
        boolean nullable) implements IntermediaryType.CheckerParamType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_REFERENCE, ReferenceType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_MULTIPLICITY, EnumType.MULTIPLICITY_ENUM),
                        new LabelDescription(ConfigurationSchemaNode.OA_IS_PARENT, new BooleanType(false)),
                        new LabelDescription(ConfigurationSchemaNode.OA_IS_RECURSIVE, new BooleanType(false))
                );
    }
    public static ReferenceCheckerParamsType  EMPTY_INSTANCE(){
        return new ReferenceCheckerParamsType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    private ReferenceCheckerParamsType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }
    public ReferenceCheckerParamsType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

}

