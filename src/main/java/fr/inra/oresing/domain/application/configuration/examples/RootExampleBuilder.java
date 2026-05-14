package fr.inra.oresing.domain.application.configuration.examples;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.RootType;

import java.util.LinkedHashMap;

public class RootExampleBuilder {
    public static RootType buildRootSchema() {
        LinkedHashMap<String, ConfigurationSchemaNodeType<?>> children = new LinkedHashMap<>();
        children.put(ConfigurationSchemaNode.OA_VERSION, StringExampleBuilder.OPENADOM_VERSION);
        children.put(ConfigurationSchemaNode.OA_APPLICATION, ApplicationDescriptionExampleBuilder.buildApplicationDesriptionSchema());
        children.put(ConfigurationSchemaNode.OA_TAGS, TagExampleBuilder.buildTagSchema());
        children.put(ConfigurationSchemaNode.OA_DATA, DataExampleBuilder.buildDataType());
        children.put(ConfigurationSchemaNode.OA_RIGHTS_REQUEST, RightRequestExampleBuilder.buildRightRequestSchema());
        children.put(ConfigurationSchemaNode.OA_ADDITIONAL_FILES, CollectionExampleBuilder.ADITIONNAL_FILES);

        return new RootType(children);
    }
}