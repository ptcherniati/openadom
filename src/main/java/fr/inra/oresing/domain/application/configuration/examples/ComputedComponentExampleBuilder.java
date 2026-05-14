package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ComputedComponentExampleBuilder {

    private ComputedComponentExampleBuilder() {
    }

    protected static final ComputedComponentType SITES = buildComputedComponentWithNaturalKeyColumns(
            List.of("zet_chemin_parent", "zet_nom_key"),
            ReferenceCheckerExampleBuilder.SITE,
            TitleExampleBuilder.DATE_TIME,
            null
    );
    protected static final ComputedComponentType DATE_HEURE = buildComputedComponent(
            StringExampleBuilder.DATE_TIME_GRROVY_EXPRESSION,
            DateCheckerExampleBuilder.DDMMYYYYHHMMSS,
            TitleExampleBuilder.DATE_TIME,
            null
    );
    protected static final ComputedComponentType DATA_DATE_HEURE = buildComputedComponent(
            StringExampleBuilder.DATA_DATE_TIME_GRROVY_EXPRESSION,
            DateCheckerExampleBuilder.DDMMYYYYHHMMSS,
            TitleExampleBuilder.DATE_TIME,
            null
    );

    static ComputedComponentType buildComputedComponent(
            final StringType computation,
            final CheckerType checker,
            final TitleType exportHeaderType,
            CollectionType.ArrayType<StringType> langRestriction) {
        Map<String, ConfigurationSchemaNodeType<?>> map = new LinkedHashMap<>();
        map.put(ConfigurationSchemaNode.OA_COMPUTATION, new GroovyExpressionType(Map.of(ConfigurationSchemaNode.OA_EXPRESSION, computation)));
        map.put(ConfigurationSchemaNode.OA_CHECKER, checker);
        map.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeaderType);
        map.put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
        return new ComputedComponentType(map);
    }

    static ComputedComponentType buildComputedComponentWithNaturalKeyColumns(final List<String> naturalKeyColumns,
                                                                             final CheckerType checker,
                                                                             final TitleType exportHeaderType,
                                                                             CollectionType.ArrayType<StringType> langRestriction) {
        CollectionType.ArrayType<StringType> naStringTypeArrayType = new CollectionType.ArrayType<>(naturalKeyColumns.stream().map(StringType::new).toList(), false, true, StringType.EMPTY_INSTANCE());
        Map<String, ConfigurationSchemaNodeType<?>> map = new LinkedHashMap<>();
        map.put(ConfigurationSchemaNode.OA_WITH_NATURAL_KEY_COMPONENTS, naStringTypeArrayType);
        map.put(ConfigurationSchemaNode.OA_CHECKER, checker);
        map.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeaderType);
        if (langRestriction != null) {
            map.put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
        }
        return new ComputedComponentType(map);
    }
}