package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public record ValidationType(SectionBuilder sectionBuilder,
                             Map<String, ConfigurationSchemaNodeType<?>> children,
                             boolean required,
                             boolean nullable) implements ApplicationType<Map<String, ConfigurationSchemaNodeType<?>>> {
    public ValidationType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private ValidationType(final Map<String, ConfigurationSchemaNodeType<?>> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_CHECKER, CheckerType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N, I18nType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_REQUIRED, BooleanType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPONENTS, new CollectionType.ArrayType<>(List.of(), false, false, StringType.EMPTY_INSTANCE()))
                );
    }

    public static ValidationType EMPTY_INSTANCE() {
        return new ValidationType(Map.of(), RootType.CHECKING.NO_CHECK);
    }
}