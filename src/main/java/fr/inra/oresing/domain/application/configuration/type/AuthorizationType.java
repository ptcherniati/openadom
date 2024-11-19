package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record AuthorizationType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                                boolean required,
                                boolean nullable) implements ApplicationType {
    public AuthorizationType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private AuthorizationType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_AUTHORIZATION_SCOPES, StaticMapType.AUTHORIZATION_SCOPES().type),
                        new LabelDescription(ConfigurationSchemaNode.OA_TIME_SCOPE, StringType.EMPTY_INSTANCE())
                );
    }

    public static AuthorizationType EMPTY_INSTANCE() {
        return new AuthorizationType(Map.of(), RootType.CHECKING.NO_CHECK);
    }
}
