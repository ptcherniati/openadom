package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ComputedComponentExampleBuilder {
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
        return new ComputedComponentType(
                new LinkedHashMap<String, ConfigurationSchemaNodeType>() {
                    {
                        put(ConfigurationSchemaNode.OA_COMPUTATION, new GroovyExpressionType(Map.of(ConfigurationSchemaNode.OA_EXPRESSION, computation)));
                        put(ConfigurationSchemaNode.OA_CHECKER, checker);
                        put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeaderType);
                        put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
                    }
                }
        );
    }

    static ComputedComponentType buildComputedComponentWithNaturalKeyColumns(final List<String> naturalKeyColumns,
                                                                             final CheckerType checker,
                                                                             final TitleType exportHeaderType,
                                                                             CollectionType.ArrayType<StringType> langRestriction) {
        CollectionType.ArrayType<StringType> naStringTypeArrayType = new CollectionType.ArrayType<>(naturalKeyColumns.stream().map(StringType::new).toList(), false, true, StringType.EMPTY_INSTANCE());
        return new ComputedComponentType(
                new LinkedHashMap<String, ConfigurationSchemaNodeType>() {
                    {
                        put(ConfigurationSchemaNode.OA_WITH_NATURAL_KEY_COMPONENTS, naStringTypeArrayType);
                        put(ConfigurationSchemaNode.OA_CHECKER, checker);
                        put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeaderType);
                        if (langRestriction != null) {
                            put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
                        }
                    }
                }
        );
    }
}