package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.ReferenceScopeType;
import fr.inra.oresing.domain.application.configuration.type.SubmissionScopeType;
import fr.inra.oresing.domain.application.configuration.type.SubmissionTimeScopeType;

import java.util.HashMap;

public class SubmissionScopeTypeExampleBuilder {
    public static final SubmissionScopeType DATA_SUBMISSION_SCOPE = buildSubmissionScope(
            ReferenceScopeTypeExampleBuilder.REFERENCE_SCOPES,
            SubmissionTimeScopeTypeExampleBuilder.TIME_SCOPE
    );

    private static SubmissionScopeType buildSubmissionScope(
            CollectionType.ArrayType<ReferenceScopeType> referenceScopeType,
            SubmissionTimeScopeType timeScopeType
    ) {
        return new SubmissionScopeType(
                new HashMap<>(){{
                    put(ConfigurationSchemaNode.OA_REFERENCE_SCOPES, referenceScopeType);
                    put(ConfigurationSchemaNode.OA_TIME_SCOPE, timeScopeType);
                }}
        );
    }
}
