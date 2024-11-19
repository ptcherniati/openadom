package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record AdditionalFileType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                                 boolean required,
                                 boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_FORM_FIELDS, ApplicationDescriptionType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N, TitleType.EMPTY_INSTANCE())
                );
    }
    public static AdditionalFileType  EMPTY_INSTANCE() {
        return new AdditionalFileType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    public AdditionalFileType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private AdditionalFileType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

}
