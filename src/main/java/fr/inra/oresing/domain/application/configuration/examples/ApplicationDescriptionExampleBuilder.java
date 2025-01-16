package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ApplicationDescriptionType;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;

import java.util.LinkedHashMap;
import java.util.Map;

class ApplicationDescriptionExampleBuilder {
    protected static ApplicationDescriptionType buildApplicationDesriptionSchema() {
        return new ApplicationDescriptionType(createApplicationDescriptionMap());
    }

    private static Map<String, ConfigurationSchemaNodeType> createApplicationDescriptionMap() {
        Map<String, ConfigurationSchemaNodeType> map = new LinkedHashMap<>();
        map.put(ConfigurationSchemaNode.OA_NAME, StringExampleBuilder.MONSORE);
        map.put(ConfigurationSchemaNode.OA_VERSION, StringExampleBuilder.INITIAL_VERSION);
        map.put(ConfigurationSchemaNode.OA_COMMENT, StringExampleBuilder.COMMENT);
        map.put(ConfigurationSchemaNode.OA_DEFAULT_LANGUAGE, StringExampleBuilder.DEFAULT_LANGUAGE);
        map.put(ConfigurationSchemaNode.OA_I_18_N, TitleExampleBuilder.SOERE_NAME);
        return map;
    }
}
