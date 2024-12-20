package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.RootType;

import java.util.LinkedHashMap;

public class RootExampleBuilder {
    public static RootType buildRootSchema() {
        return new RootType(new LinkedHashMap<>() {{
            put(ConfigurationSchemaNode.OA_VERSION, StringExampleBuilder.OPENADOM_VERSION);
            put(ConfigurationSchemaNode.OA_APPLICATION, ApplicationDescriptionExampleBuilder.buildApplicationDesriptionSchema());
            put(ConfigurationSchemaNode.OA_TAGS, TagExampleBuilder.buildTagSchema());
            put(ConfigurationSchemaNode.OA_DATA, DataExampleBuilder.buildDataType());
            put(ConfigurationSchemaNode.OA_RIGHTS_REQUEST, RightRequestExampleBuilder.buildRightRequestSchema());
            put(ConfigurationSchemaNode.OA_ADDITIONAL_FILES, CollectionExampleBuilder.ADITIONNAL_FILES);
        }});
    }
}