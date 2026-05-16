package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.Map;

class SumissionTypeExampleBuilder {
    protected static final SubmissionType DATA_SUBMISSION = buildSubmission(
            StringExampleBuilder.REPOSITORY,
            SubmissionScopeTypeExampleBuilder.DATA_SUBMISSION_SCOPE,
            SubmissionFileNameTypeExampleBuilder.DATA_FILE_NAME
    );

    static SubmissionType buildSubmission(
            final StringType strategy,
            final SubmissionScopeType submissionScope,
            final FileNameType columnToLookup
    ) {
        Map<String, ConfigurationSchemaNodeType<?>> children = new LinkedHashMap<>();
        children.put(ConfigurationSchemaNode.OA_STRATEGY, strategy);
        children.put(ConfigurationSchemaNode.OA_SUBMISSION_SCOPE, submissionScope);
        children.put(ConfigurationSchemaNode.OA_FILE_NAME, columnToLookup);

        return new SubmissionType(children);
    }
}