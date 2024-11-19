package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PatternComponentQualifierExampleBuilder {
    protected static final PatternComponentQualifierType PROFONDEUR = buildPatternComponentComponentType(
            TitleExampleBuilder.PROFONDEUR,
            List.of("data"),
            FloatCheckerExampleBuilder.PROFONDEUR
    );
    protected static final PatternComponentQualifierType REPETITION = buildPatternComponentComponentType(
            TitleExampleBuilder.REPETITION,
            List.of("data"),
            IntegerCheckerExampleBuilder.REPETITION
    );

    protected static PatternComponentQualifierType buildPatternComponentComponentType(
            final TitleType exportHeader,
            final List<String> tags,
            final CheckerType checker) {
        return new PatternComponentQualifierType(new LinkedHashMap<String, ConfigurationSchemaNodeType>() {{
            put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeader);
            put(ConfigurationSchemaNode.OA_REQUIRED, BooleanExampleBuilder.TRUE);
            put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<StringType>(TagExampleBuilder.buildTagArray(tags), false, false, StringType.EMPTY_INSTANCE()));
            put(ConfigurationSchemaNode.OA_CHECKER, checker);
        }}
        );
    }

    protected static PatternComponentQualifierType buildBasicComponents(
            final List<String> tags,
            final boolean required,
            final String importHeader,
            final TitleType exportHeader,
            final CheckerType checker,
            CollectionType.ArrayType<StringType> langRestriction
    ) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<String, ConfigurationSchemaNodeType>();
        children.put(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(required, false));
        if (importHeader != null) children.put(ConfigurationSchemaNode.OA_IMPORT_HEADER, new StringType(importHeader));
        if (CollectionUtils.isNotEmpty(tags)) {
            final List<StringType> tagsArray = tags.stream().map(StringType::new).toList();
            children.put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<StringType>(tagsArray, false, false, StringType.EMPTY_INSTANCE()));
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
        return new PatternComponentQualifierType(children);
    }
}