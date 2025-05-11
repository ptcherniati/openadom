package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class StringCheckerExampleBuilder {
    protected static final StringCheckerType ESPECE = buildStringChecker("SPE_.*", Multiplicity.ONE);
    protected static final CheckerType ALL = buildStringChecker(".*", Multiplicity.ONE);
    protected static StringCheckerType buildStringChecker(final String pattern, final Multiplicity multiplicity) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<>();
        final HashMap<String, ConfigurationSchemaNodeType> params = new HashMap<>();
        final StringType oaPattern = Optional.ofNullable(pattern)
                .map(StringType::new)
                .orElse(new StringType(".*"));
        params.put(ConfigurationSchemaNode.OA_PATTERN,
                oaPattern
        );
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        children.put(ConfigurationSchemaNode.OA_PARAMS, new StringCheckerParamsType(params));
        return new StringCheckerType(children);
    }
}