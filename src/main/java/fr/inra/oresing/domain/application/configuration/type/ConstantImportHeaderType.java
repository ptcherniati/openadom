package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;

public record ConstantImportHeaderType(SectionBuilder sectionBuilder,
                                       Map<String, ConfigurationSchemaNodeType<?>> children,
                                       boolean required,
                                       boolean nullable) implements ApplicationType {
    private ConstantImportHeaderType(final Map<String, ConfigurationSchemaNodeType<?>> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public ConstantImportHeaderType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_ROW_NUMBER, StringType.EMPTY_INSTANCE())
                )
                .withAnyOfMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NAME, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER, IntegerType.EMPTY_INSTANCE())
                );
    }

    public static ConstantImportHeaderType EMPTY_INSTANCE() {
        return new ConstantImportHeaderType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

}