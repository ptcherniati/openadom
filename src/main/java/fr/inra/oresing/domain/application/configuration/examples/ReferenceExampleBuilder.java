package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.BooleanType;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.ReferenceType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.HashMap;
import java.util.Map;

class ReferenceExampleBuilder {

    protected static ReferenceType buildReference(final String reference, final boolean isParent, final boolean isRecursive) {
        final Map<String, ConfigurationSchemaNodeType<?>> children = new HashMap<>();
        children.put(ConfigurationSchemaNode.OA_NAME, new StringType(reference, true));
        if (isParent) {
            children.put(ConfigurationSchemaNode.OA_IS_PARENT, new BooleanType(true));
        }
        if (isRecursive) {
            children.put(ConfigurationSchemaNode.OA_IS_RECURSIVE, new BooleanType(true));
        }
        return new ReferenceType(children);
    }
}