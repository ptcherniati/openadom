package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.StringType;
import fr.inra.oresing.domain.application.configuration.type.SubmissionTimeScopeType;

import java.util.HashMap;

public class SubmissionTimeScopeTypeExampleBuilder {
    public static final SubmissionTimeScopeType TIME_SCOPE = builTimeScope();

    private static SubmissionTimeScopeType builTimeScope() {
        return new SubmissionTimeScopeType(
                new HashMap<>(new HashMap<>() {{
                    put(ConfigurationSchemaNode.OA_COMPONENT, new StringType("dat_date_heure", true));
                }})
        );
    }
}
