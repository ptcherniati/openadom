package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.HashMap;
import java.util.List;

public class SubmissionFileNameTypeExampleBuilder {
    public static final FileNameType DATA_FILE_NAME = buildFileName(
            new StringType("(.*)_(.*)_(.*).csv", true),
            new CollectionType.ArrayType<>(
                    List.of(
                            new StringType("dat_site", false),
                            new StringType(ConfigurationSchemaNode.OA_START_DATE_MATCH_PATTERN, false),
                            new StringType(ConfigurationSchemaNode.OA_END_DATE_MATCH_PATTERN, false)
                    ),
                    false,
                    true,
                    StringType.EMPTY_INSTANCE()
            )
    );

    private static FileNameType buildFileName(
            StringType fileNamePattern,
            CollectionType.ArrayType<StringType> referenceScopeType
    ) {
        return new FileNameType(
                new HashMap<>() {{
                    put(ConfigurationSchemaNode.OA_FILE_PATTERN, fileNamePattern);
                    put(ConfigurationSchemaNode.OA_MATCH_PATTERN_SCOPES, referenceScopeType);
                }}
        );
    }
}
