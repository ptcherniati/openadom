package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.DynamicComponentType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.LinkedHashMap;

class DynamicComponentsExampleBuilder {
    protected static final DynamicComponentType PROPRIETE_TAXON = buildDynamicComponent(
            StringExampleBuilder.PROPRIETE_TAXON_HEADER_PREFIX,
            StringExampleBuilder.PROPRIETE_TAXON_REFERENCE,
            StringExampleBuilder.PROPRIETE_TAXON_REFERENCE_TO_LOOKUP,
            null
    );

    static DynamicComponentType buildDynamicComponent(
            final StringType prefix,
            final StringType reference,
            final StringType columnToLookup,
            CollectionType.ArrayType<StringType> langRestriction
    ) {
        return new DynamicComponentType(new LinkedHashMap<String, ConfigurationSchemaNodeType>() {
            {
                put(ConfigurationSchemaNode.OA_HEADER_PREFIX, prefix);
                put(ConfigurationSchemaNode.OA_REFERENCE, reference);
                put(ConfigurationSchemaNode.OA_REFERENCE_COMPONENT_TO_LOOK_FOR_HEADER, columnToLookup);
                if (langRestriction != null) {
                    put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
                }
            }
        });
    }
}