package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.ReferenceScopeType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.LinkedHashMap;
import java.util.List;

public class ReferenceScopeTypeExampleBuilder {
    public static final CollectionType.ArrayType<ReferenceScopeType> REFERENCE_SCOPES = buildReferenceScopes(
            List.of(
                    new ReferenceScopeType(new LinkedHashMap<>(){{
                        put(ConfigurationSchemaNode.OA_COMPONENT, new StringType("dat_site", true));
                        put(ConfigurationSchemaNode.OA_REFERENCE, new StringType("tr_zone_etude_zet", false));
                        put(ConfigurationSchemaNode.OA_I_18_N, TitleExampleBuilder.SITE);
                        put(ConfigurationSchemaNode.OA_EXPORT_HEADER, TitleExampleBuilder.SITE);
                    }})
            )
    );

    private static CollectionType.ArrayType<ReferenceScopeType> buildReferenceScopes(List<ReferenceScopeType> referenceScopeTypes) {
        return new CollectionType.ArrayType<>(
                referenceScopeTypes,
                false,
                false,
                ReferenceScopeType.EMPTY_INSTANCE()
        );
    }
}
