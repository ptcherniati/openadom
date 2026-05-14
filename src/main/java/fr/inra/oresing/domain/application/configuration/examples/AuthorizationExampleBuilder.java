package fr.inra.oresing.domain.application.configuration.examples;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.AuthorizationType;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class AuthorizationExampleBuilder {
    public static final AuthorizationType DATA_AUTHORIZATION = buildAuthorization(
            List.of(new StringType("dat_site", false)),
            new StringType("dat_date_heure", false)
    );

    private AuthorizationExampleBuilder() {
    }

    protected static AuthorizationType buildAuthorization(
            List<StringType> authorizationsScope,
            StringType timeScope) {
        Map<String, ConfigurationSchemaNodeType<?>> children = new LinkedHashMap<>();
        children.put(ConfigurationSchemaNode.OA_AUTHORIZATION_SCOPES, new CollectionType.ArrayType<>(
                authorizationsScope,
                false,
                false,
                StringType.EMPTY_INSTANCE()
        ));
        children.put(ConfigurationSchemaNode.OA_TIME_SCOPE, timeScope);

        return new AuthorizationType(
                children
        );
    }
}