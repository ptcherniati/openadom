package fr.inra.oresing.domain.application.configuration.type;


import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record ReferenceCheckerType(SectionBuilder sectionBuilder,
                                   Map<String, ConfigurationSchemaNodeType<?>> children,
                                   boolean required,
                                   boolean nullable) implements CheckerType {

    public ReferenceCheckerType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public ReferenceCheckerType(final SectionBuilder sectionBuilder, final Map<String, ConfigurationSchemaNodeType<?>> children, final boolean required, final boolean nullable) {
        this.children = addNameNode(children);
        this.sectionBuilder = sectionBuilder
                .test(children().keySet());
        this.required = required;
        this.nullable = nullable;
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_NAME, EnumType.CHECKER_NAME_ENUM),
                        new LabelDescription(ConfigurationSchemaNode.OA_PARAMS, ReferenceCheckerParamsType.EMPTY_INSTANCE())
                );
    }

    public static ReferenceCheckerType EMPTY_INSTANCE() {
        return new ReferenceCheckerType(
                Map.of(
                        ConfigurationSchemaNode.OA_NAME, new StringType(CheckerEnum.OA_reference.name()),
                        ConfigurationSchemaNode.OA_PARAMS, ReferenceCheckerParamsType.EMPTY_INSTANCE()
                ));
    }

    @Override
    public CheckerEnum getChecker() {
        return CheckerEnum.OA_reference;
    }
}