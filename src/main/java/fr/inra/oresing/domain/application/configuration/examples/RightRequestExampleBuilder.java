package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.FormatType;
import fr.inra.oresing.domain.application.configuration.type.RightRequestType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class RightRequestExampleBuilder {
    protected static RightRequestType buildRightRequestSchema() {
        final Map<String, ConfigurationSchemaNodeType> children = new LinkedHashMap<>() {{
            put(ConfigurationSchemaNode.OA_I_18_N, TitleExampleBuilder.RIGHT_REQUEST_DESCRIPTION);
        }};
        final Map<String, FormatType> formats = new HashMap<>();

        children.put(ConfigurationSchemaNode.OA_FORM_FIELDS, CollectionExampleBuilder.RIGHT_REQUEST_FORM_FIELDS);
        return new RightRequestType(children);
    }
}