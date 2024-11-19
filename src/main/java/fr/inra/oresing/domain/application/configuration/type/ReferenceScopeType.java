package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record ReferenceScopeType(SectionBuilder sectionBuilder,
                                 Map<String, ConfigurationSchemaNodeType> children,
                                 boolean required,
                                 boolean nullable) implements ApplicationType {
    private ReferenceScopeType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public ReferenceScopeType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_REFERENCE, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPONENT, StringType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N
, TitleType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_EXPORT_HEADER, TitleType.EMPTY_INSTANCE())
                );
    }

    public static ReferenceScopeType EMPTY_INSTANCE() {
        return new ReferenceScopeType(Map.of(), RootType.CHECKING.NO_CHECK);
    }
}
