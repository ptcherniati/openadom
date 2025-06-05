package fr.inra.oresing.domain.application.configuration.type;


import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record IntegerCheckerType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType<?>> children,
                                 boolean required,
                                 boolean nullable) implements CheckerType {

    public IntegerCheckerType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public IntegerCheckerType(final SectionBuilder sectionBuilder, final Map<String, ConfigurationSchemaNodeType<?>> children, final boolean required, final boolean nullable) {
        this.children = addNameNode(children);
        this.sectionBuilder = sectionBuilder
                .test(children().keySet());
        this.required = required;
        this.nullable = nullable;
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_NAME, EnumType.CHECKER_NAME_ENUM)
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_PARAMS, IntegerCheckerParamsType.EMPTY_INSTANCE())
                );
    }

    public static IntegerCheckerType EMPTY_INSTANCE() {
        return new IntegerCheckerType(
                Map.of(
                        ConfigurationSchemaNode.OA_NAME, new StringType(CheckerEnum.OA_integer.name()),
                        ConfigurationSchemaNode.OA_PARAMS, IntegerCheckerParamsType.EMPTY_INSTANCE()
                ));
    }

    @Override
    public CheckerEnum getChecker() {
        return CheckerEnum.OA_integer;
    }
}