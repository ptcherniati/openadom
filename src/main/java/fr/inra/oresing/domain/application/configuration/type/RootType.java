package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record RootType(SectionBuilder sectionBuilder,
                       Map<String, ConfigurationSchemaNodeType> children, boolean required,
                       boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_VERSION, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_APPLICATION, ApplicationDescriptionType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_DATA, DataType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_TAGS, TagType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_RIGHTS_REQUEST, RightRequestType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_ADDITIONAL_FILES, AdditionalFileType.EMPTY_INSTANCE())
                );
    }
    public enum CHECKING{NO_CHECK}
    public static RootType  EMPTY_INSTANCE() {
        return new RootType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    public RootType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }
    public RootType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    @Override
    public String buildExample(final int level) {
        final StringBuilder builder = new StringBuilder();
        for (final Map.Entry<String, ConfigurationSchemaNodeType> entry : children.entrySet()) {
            final String label = entry.getKey();
            final ConfigurationSchemaNodeType value = entry.getValue();
            if (value==null){
                continue;
            }
            builder.append(" \n%1$s%2$s: %3$s".formatted(Strings.repeat("  ", level).replaceAll("^\\.",""), label, value.buildExample(level + 1)));
        }
        return builder.toString()
                .replaceAll("^\\n$","\n")
                .replaceAll("\\s*\\n(\\s*\\n)*", "\n")
                .replaceAll("^\\n", "");
    }
}
