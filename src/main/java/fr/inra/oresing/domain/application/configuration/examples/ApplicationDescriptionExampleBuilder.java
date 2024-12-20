package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ApplicationDescriptionType;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;

import java.util.LinkedHashMap;

class ApplicationDescriptionExampleBuilder {
    protected static ApplicationDescriptionType buildApplicationDesriptionSchema() {
        return new ApplicationDescriptionType(new LinkedHashMap<>() {{
            put(ConfigurationSchemaNode.OA_NAME, StringExampleBuilder.MONSORE);
            put(ConfigurationSchemaNode.OA_VERSION, StringExampleBuilder.INITIAL_VERSION);
            put(ConfigurationSchemaNode.OA_COMMENT, StringExampleBuilder.COMMENT);
            put(ConfigurationSchemaNode.OA_DEFAULT_LANGUAGE, StringExampleBuilder.DEFAULT_LANGUAGE);
            put(ConfigurationSchemaNode.OA_I_18_N, TitleExampleBuilder.SOERE_NAME);
        }});
    }
}