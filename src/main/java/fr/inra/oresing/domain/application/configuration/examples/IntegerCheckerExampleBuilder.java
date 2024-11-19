package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class IntegerCheckerExampleBuilder {
    public static final CheckerType QUALITY_CLASS = buildIntegerChecker(0, 2, Multiplicity.ONE);
    protected static final IntegerCheckerType ZERO_DIX= buildIntegerChecker(0, 10, Multiplicity.ONE);
    protected static final IntegerCheckerType REPETITION= buildIntegerChecker(0, 10, Multiplicity.ONE);

    protected static IntegerCheckerType buildIntegerChecker(final Integer min, final Integer max, final Multiplicity multiplicity) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<String, ConfigurationSchemaNodeType>();
        final HashMap<String, ConfigurationSchemaNodeType> params = new HashMap<String, ConfigurationSchemaNodeType>();
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        Optional.ofNullable(min)
                .map(IntegerType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_MIN, m));
        Optional.ofNullable(max)
                .map(IntegerType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_MAX, m));
        children.put(ConfigurationSchemaNode.OA_PARAMS, new IntegerCheckerParamsType(params));
        return new IntegerCheckerType(children);
    }
}