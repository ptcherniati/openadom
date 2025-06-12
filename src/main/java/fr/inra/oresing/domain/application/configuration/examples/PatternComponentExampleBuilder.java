package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PatternComponentExampleBuilder {
    protected static final String SWC_PATTERN = "\"SWC_(.*)_(.*)\"";
    protected static final String SMP_PATTERN = "\"SMP_(.*)_(.*)\"";
    protected static final PatternComponentType SWC = buildPatternComponent(
            SWC_PATTERN,
            TitleExampleBuilder.SWC,
            "swc"
    );
    protected static final PatternComponentType SMP = buildPatternComponent(
            SMP_PATTERN,
            TitleExampleBuilder.SMP,
            "smp"
    );

    static PatternComponentType buildPatternComponent(
            final String pattern,
            final TitleType exportHeader,
            final String prefix
    ) {
        LinkedHashMap<String, ConfigurationSchemaNodeType<?>> children = new LinkedHashMap<>();

        children.put(ConfigurationSchemaNode.OA_PATTERN_FOR_COMPONENTS, new StringType(pattern));
        children.put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(
                TagExampleBuilder.buildTagArray(List.of("context")),
                false,
                false,
                StringType.EMPTY_INSTANCE()
        ));
        children.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeader);
        children.put(ConfigurationSchemaNode.OA_REQUIRED, BooleanExampleBuilder.FALSE);
        children.put(ConfigurationSchemaNode.OA_CHECKER, FloatCheckerExampleBuilder.OF);
        children.put(ConfigurationSchemaNode.OA_COMPONENT_QUALIFIERS, CollectionExampleBuilder.COMPONENT_QUALIFIERS(prefix));
        children.put(ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS, CollectionExampleBuilder.COMPONENT_ADJACENTS(prefix));

        return new PatternComponentType(children);
    }

    protected static PatternComponentType buildPatternComponents(
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
        return new PatternComponentType(children);
    }
}