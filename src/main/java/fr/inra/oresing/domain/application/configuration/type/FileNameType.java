package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record FileNameType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                           boolean required,
                           boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_FILE_PATTERN, StringType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_MATCH_PATTERN_SCOPES, StaticMapType.FILE_MATCH_PATTERN_SCOPES().type)
                );
    }
    public static FileNameType  EMPTY_INSTANCE(){
        return new FileNameType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    private FileNameType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public FileNameType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }
}
