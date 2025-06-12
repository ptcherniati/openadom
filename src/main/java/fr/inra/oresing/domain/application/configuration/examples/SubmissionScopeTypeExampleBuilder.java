package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.HashMap;
import java.util.Map;

public class SubmissionScopeTypeExampleBuilder {
    public static final SubmissionScopeType DATA_SUBMISSION_SCOPE = buildSubmissionScope(
            ReferenceScopeTypeExampleBuilder.REFERENCE_SCOPES,
            SubmissionTimeScopeTypeExampleBuilder.TIME_SCOPE
    );

    private static SubmissionScopeType buildSubmissionScope(
            CollectionType.ArrayType<ReferenceScopeType> referenceScopeType,
            SubmissionTimeScopeType timeScopeType
    ) {
        Map<String, ConfigurationSchemaNodeType<?>> map = new HashMap<>();
        map.put(ConfigurationSchemaNode.OA_REFERENCE_SCOPES, referenceScopeType);
        map.put(ConfigurationSchemaNode.OA_TIME_SCOPE, timeScopeType);

        return new SubmissionScopeType(map);
    }

}