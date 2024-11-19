package fr.inra.oresing.domain.application.configuration.type;



import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record DateCheckerType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                              boolean required,
                              boolean nullable) implements CheckerType {

    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_NAME, EnumType.CHECKER_NAME_ENUM),
                        new LabelDescription(ConfigurationSchemaNode.OA_PARAMS, DateCheckerParamsType.EMPTY_INSTANCE()));
    }
    public static DateCheckerType  EMPTY_INSTANCE() {
        return new DateCheckerType(
                Map.of(
                        ConfigurationSchemaNode.OA_NAME, new StringType(CheckerEnum.OA_date.name()),
                        ConfigurationSchemaNode.OA_PARAMS, DateCheckerParamsType.EMPTY_INSTANCE()
                ));
    }
    public DateCheckerType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public DateCheckerType(final SectionBuilder sectionBuilder, final Map<String, ConfigurationSchemaNodeType> children, final boolean required, final boolean nullable) {
        this.children = addNameNode(children);
        this.sectionBuilder = sectionBuilder
                .test(children().keySet());
        this.required = required;
        this.nullable = nullable;
    }

    @Override
    public CheckerEnum getChecker() {
        return CheckerEnum.OA_date;
    }
}
