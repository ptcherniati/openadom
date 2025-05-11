package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class FloatCheckerExampleBuilder {
    protected static final FloatCheckerType OF = buildFloatChecker(0f, 2000f, Multiplicity.ONE);
    protected static final CheckerType PROFONDEUR = buildFloatChecker(0f, 500f, Multiplicity.ONE);
    protected static final CheckerType STANDARD_DEVIATION = buildFloatChecker(0f, 500f, Multiplicity.ONE);

    protected static FloatCheckerType buildFloatChecker(final Float min, final Float max, final Multiplicity multiplicity) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<>();
        final HashMap<String, ConfigurationSchemaNodeType> params = new HashMap<>();
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        Optional.ofNullable(min)
                .map(FloatType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_MIN, m));
        Optional.ofNullable(max)
                .map(FloatType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_MAX, m));
        children.put(ConfigurationSchemaNode.OA_PARAMS, new FloatCheckerParamsType(params));
        return new FloatCheckerType(children);
    }
}