package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record ApplicationDescriptionType<T extends Map<String, ConfigurationSchemaNodeType<?>>>(
        SectionBuilder sectionBuilder,
        T children,
        boolean required,
        boolean nullable) implements ApplicationType<T> {
    public ApplicationDescriptionType(final T children) {
        this(builder()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private ApplicationDescriptionType(final T children, final RootType.CHECKING checking) {
        this(builder(),
                children,
                true,
                false);
    }

    public static SectionBuilder builder() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_NAME, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_VERSION, StringType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N
                                , TitleType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_COMMENT, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DEFAULT_LANGUAGE, StringType.EMPTY_INSTANCE())
                );
    }

    public static ApplicationDescriptionType emptyInstance() {
        return new ApplicationDescriptionType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

}