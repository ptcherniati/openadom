package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PatternComponentAdjacentExampleBuilder {
    protected static final PatternComponentAdjacentType STANDARD_DEVIATION = buildPatternComponentAdjacentType(
            "\"{$1}_sd\"",
            false,
            false,
            TitleExampleBuilder.STANDARD_DEVIATION,
            List.of("data"),
            FloatCheckerExampleBuilder.STANDARD_DEVIATION
    );
    protected static final PatternComponentAdjacentType QUALITY_CLASS = buildPatternComponentAdjacentType(
            "\"{$1}_qc\"",
            false,
            false,
            TitleExampleBuilder.QUALITY_CLASS,
            List.of("data"),
            IntegerCheckerExampleBuilder.QUALITY_CLASS
    );

    protected static PatternComponentAdjacentType buildPatternComponentAdjacentType(
            final String importHeaderPattern,
            final boolean required,
            final boolean mandatory,
            final TitleType exportHeader,
            final List<String> tags,
            final CheckerType checker) {
        return new PatternComponentAdjacentType(new LinkedHashMap<>() {{
            put(ConfigurationSchemaNode.OA_IMPORT_HEADER_PATTERN, new StringType(importHeaderPattern));
            put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeader);
            put(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(required));
            put(ConfigurationSchemaNode.OA_MANDATORY, new BooleanType(mandatory));
            put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(TagExampleBuilder.buildTagArray(tags), false, false, StringType.EMPTY_INSTANCE()));
            put(ConfigurationSchemaNode.OA_CHECKER, checker);
        }}
        );
    }

    protected static PatternComponentAdjacentType buildBasicComponents(
            final List<String> tags,
            final boolean required,
            final String importHeader,
            final TitleType exportHeader,
            final CheckerType checker,
            CollectionType.ArrayType<StringType> langRestriction
    ) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<>();
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
        return new PatternComponentAdjacentType(children);
    }
}