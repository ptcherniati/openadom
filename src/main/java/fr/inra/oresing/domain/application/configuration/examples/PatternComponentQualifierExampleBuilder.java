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
        LinkedHashMap<String, ConfigurationSchemaNodeType> map = new LinkedHashMap<>();

        map.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeader);
        map.put(ConfigurationSchemaNode.OA_REQUIRED, BooleanExampleBuilder.TRUE);
        map.put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(
                TagExampleBuilder.buildTagArray(tags),
                false,
                false,
                StringType.EMPTY_INSTANCE()
        ));
        map.put(ConfigurationSchemaNode.OA_CHECKER, checker);

        return new PatternComponentQualifierType(map);
    }


    protected static PatternComponentQualifierType buildBasicComponents(
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
        return new PatternComponentQualifierType(children);
    }
}