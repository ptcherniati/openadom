package fr.inra.oresing.domain.application.configuration.examples;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.StringType;
import fr.inra.oresing.domain.application.configuration.type.SubmissionTimeScopeType;

import java.util.HashMap;
import java.util.Map;

public class SubmissionTimeScopeTypeExampleBuilder {
    public static final SubmissionTimeScopeType TIME_SCOPE = buildTimeScope();

    private static SubmissionTimeScopeType buildTimeScope() {
        Map<String, ConfigurationSchemaNodeType<?>> map = new HashMap<>();
        map.put(ConfigurationSchemaNode.OA_COMPONENT, new StringType("dat_date_heure", true));

        return new SubmissionTimeScopeType(map);
    }
}