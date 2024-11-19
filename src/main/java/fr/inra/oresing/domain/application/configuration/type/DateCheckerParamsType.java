package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record DateCheckerParamsType(SectionBuilder sectionBuilder,
                                    Map<String, ConfigurationSchemaNodeType> children, boolean required,
                                    boolean nullable) implements IntermediaryType.CheckerParamType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_PATTERN, StringType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_MIN, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_MAX, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DURATION, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_MULTIPLICITY, EnumType.MULTIPLICITY_ENUM)
                );
    }
    public static DateCheckerParamsType  EMPTY_INSTANCE() {
        return new DateCheckerParamsType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    public DateCheckerParamsType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }
    private DateCheckerParamsType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

}
