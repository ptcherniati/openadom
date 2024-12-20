package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.List;
import java.util.Map;

public record DataType(SectionBuilder sectionBuilder, Map<String, ConfigurationSchemaNodeType> children,
                       boolean required,
                       boolean nullable) implements ApplicationType {
    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_NATURAL_KEY, new CollectionType.ArrayType<>(List.of(), true, false, StringType.EMPTY_INSTANCE())))
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_ALLOW_UNEXPECTED_COLUMNS, new BooleanType(true)),
                        new LabelDescription(ConfigurationSchemaNode.OA_SEPARATOR, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(List.of(), false, true, StringType.EMPTY_INSTANCE())),
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N
, TitleType
                                .EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_I_18_N_DISPLAY_PATTERN, TitleType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DATA_HEADER_LINE, IntegerType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DATA_FIRST_LINE, IntegerType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_AUTHORIZATION, AuthorizationType.EMPTY_INSTANCE())
                )
                .withAnyOfMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_BASIC_COMPONENTS, BasicComponentType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS, ComputedComponentType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS, DynamicComponentType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_PATTERN_COMPONENTS, PatternComponentQualifierType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS, ConstantComponentType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_VALIDATIONS, StaticMapType.VALIDATIONS().type),
                        new LabelDescription(ConfigurationSchemaNode.OA_SUBMISSION, SubmissionType.EMPTY_INSTANCE())
                );
    }
    public static DataType  EMPTY_INSTANCE() {
        return new DataType(Map.of(), RootType.CHECKING.NO_CHECK);
    }


    public DataType(final Map<String, ConfigurationSchemaNodeType> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private DataType(final Map<String, ConfigurationSchemaNodeType> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }
}
