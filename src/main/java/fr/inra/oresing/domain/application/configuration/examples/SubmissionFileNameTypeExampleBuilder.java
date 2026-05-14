package fr.inra.oresing.domain.application.configuration.examples;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.CollectionType;
import fr.inra.oresing.domain.application.configuration.type.FileNameType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, ConfigurationSchemaNodeType<?>> map = new HashMap<>();
        map.put(ConfigurationSchemaNode.OA_FILE_PATTERN, fileNamePattern);
        map.put(ConfigurationSchemaNode.OA_MATCH_PATTERN_SCOPES, referenceScopeType);

        return new FileNameType(map);
    }

}