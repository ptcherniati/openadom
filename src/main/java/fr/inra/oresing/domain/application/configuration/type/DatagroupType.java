package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public record DatagroupType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                            boolean required,
                            boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N, TitleType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPONENTS, new CollectionType.ArrayType<>(List.of(), false, true, StringType.EMPTY_INSTANCE()))
                );
    }
    public static DatagroupType  EMPTY_INSTANCE() {
        return new DatagroupType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    private DatagroupType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public DatagroupType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }
}
