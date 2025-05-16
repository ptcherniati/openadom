package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record ReferenceType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType<?>> children,
                            boolean required,
                            boolean nullable) implements ApplicationType {
    private ReferenceType(final Map<String, ConfigurationSchemaNodeType<?>> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public ReferenceType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_NAME, StringType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_IS_PARENT, new BooleanType(false)),
                        new LabelDescription(ConfigurationSchemaNode.OA_IS_RECURSIVE, new BooleanType(false))
                );
    }

    public static ReferenceType EMPTY_INSTANCE() {
        return new ReferenceType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

}