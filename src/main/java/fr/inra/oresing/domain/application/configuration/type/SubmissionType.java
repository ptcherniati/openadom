package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record SubmissionType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                             boolean required,
                             boolean nullable) implements ApplicationType {
    private SubmissionType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public SubmissionType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_STRATEGY, EnumType.STRATEGY_ENUM)
                )
                .withAnyOfMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_SUBMISSION_SCOPE, SubmissionScopeType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_FILE_NAME, FileNameType.EMPTY_INSTANCE())
                );
    }

    public static SubmissionType EMPTY_INSTANCE() {
        return new SubmissionType(Map.of(), RootType.CHECKING.NO_CHECK);
    }
}
