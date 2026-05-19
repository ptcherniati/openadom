package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ConstantComponentExampleBuilder {
    protected static final ConstantComponentType TYPE_SITE = buildConstantType(
            TitleExampleBuilder.TYPE_SITE_COLUMN,
            true,
            ConstantImportHeaderExampleBuilder.buildConstante(1, 2, null, null)
    );
    protected static final ConstantComponentType SITE = buildConstantType(
            TitleExampleBuilder.SITE_COLUMN,
            true,
            ConstantImportHeaderExampleBuilder.buildConstante(2, 2, null, null)
    );
    protected static final ConstantComponentType START_DATE = buildConstantType(
            TitleExampleBuilder.START_DATE_COLUMN,
            false,
            ConstantImportHeaderExampleBuilder.buildConstante(5, 0, "dat_date", null)
    );
    protected static final ConstantComponentType END_DATE = buildConstantType(
            TitleExampleBuilder.END_DATE,
            false,
            ConstantImportHeaderExampleBuilder.buildConstante(6, 0, "dat_date", null)
    );


    static ConstantComponentType buildConstantType(
            final TitleType exportHeaderType,
            final boolean required,
            final ConstantImportHeaderType constantImportHeaderType
    ) {
        return new ConstantComponentType(
                getChildren(exportHeaderType, required, constantImportHeaderType)
        );
    }

    private static LinkedHashMap<String, ConfigurationSchemaNodeType<?>> getChildren(
            TitleType exportHeaderType,
            boolean required,
            ConstantImportHeaderType constantImportHeaderType) {
        LinkedHashMap<String, ConfigurationSchemaNodeType<?>> children = new LinkedHashMap<>();
        children.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeaderType);
        children.put(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(required));
        children.put(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_TARGET, constantImportHeaderType);
        return children;
    }


    protected static ConstantComponentType buildBasicComponents(
            final List<String> tags,
            final boolean required,
            final String importHeader,
            final TitleType exportHeader,
            final CheckerType checker,
            CollectionType.ArrayType<StringType> langRestriction
    ) {
        final Map<String, ConfigurationSchemaNodeType<?>> children = new HashMap<>();
        children.put(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(required, false));
        if (importHeader != null) children.put(ConfigurationSchemaNode.OA_IMPORT_HEADER, new StringType(importHeader));
        if (CollectionUtils.isNotEmpty(tags)) {
            final List<StringType> tagsArray = tags.stream().map(StringType::new).toList();
            children.put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(tagsArray, false, false, StringType.EMPTY_INSTANCE()));
        }
        if (exportHeader != null) {
            children.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeader);
        }
        if (checker != null) {
            children.put(ConfigurationSchemaNode.OA_CHECKER, checker);
        }
        if (langRestriction != null) {
            children.put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
        }
        return new ConstantComponentType(children);
    }
}