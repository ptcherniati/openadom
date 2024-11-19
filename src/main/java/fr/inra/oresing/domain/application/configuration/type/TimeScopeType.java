package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record TimeScopeType(SectionBuilder sectionBuilder,
                            Map<String, ConfigurationSchemaNodeType> children,
                            boolean required,
                            boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER(){
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPONENT, StringType.EMPTY_INSTANCE())
                );
    }
    public static TimeScopeType  EMPTY_INSTANCE(){
        return new TimeScopeType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    private TimeScopeType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public TimeScopeType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }
}
