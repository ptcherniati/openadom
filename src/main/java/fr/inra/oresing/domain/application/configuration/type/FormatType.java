package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record FormatType(SectionBuilder sectionBuilder,
                         Map<String, ConfigurationSchemaNodeType> children,
                         boolean required,
                         boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(false)),
                        new LabelDescription(ConfigurationSchemaNode.OA_CHECKER, CheckerType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N
, TitleType.EMPTY_INSTANCE())
                );
    }
    public static FormatType  EMPTY_INSTANCE() {
        return new FormatType(Map.of(
                ConfigurationSchemaNode.OA_REQUIRED, BooleanType.EMPTY_INSTANCE(),
                ConfigurationSchemaNode.OA_CHECKER, CheckerType.EMPTY_INSTANCE(),
                ConfigurationSchemaNode.OA_I_18_N, I18nType.EMPTY_INSTANCE()
        ), RootType.CHECKING.NO_CHECK);
    }
    public FormatType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }
    private FormatType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

}
