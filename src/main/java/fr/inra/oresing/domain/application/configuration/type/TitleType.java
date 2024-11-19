package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record TitleType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                        boolean required,
                        boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withAnyOfMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_TITLE, I18nType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DESCRIPTION, I18nType.EMPTY_INSTANCE())
                );
    }

    public static TitleType  EMPTY_INSTANCE() {
        return new TitleType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

    public TitleType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private TitleType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }
}
