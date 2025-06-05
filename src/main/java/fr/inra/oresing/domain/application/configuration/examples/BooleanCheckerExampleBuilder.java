package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.BooleanCheckerParamsType;
import fr.inra.oresing.domain.application.configuration.type.BooleanCheckerType;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.EnumType;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.Map;

class BooleanCheckerExampleBuilder {
    private BooleanCheckerExampleBuilder() {
    }

    protected static final BooleanCheckerType BOOLEAN_CHECKER_TYPE = buildBooleanChecker(Multiplicity.ONE);

    protected static BooleanCheckerType buildBooleanChecker(final Multiplicity multiplicity) {
        final Map<String, ConfigurationSchemaNodeType<?>> children = new HashMap<>();
        final HashMap<String, ConfigurationSchemaNodeType<?>> params = new HashMap<>();
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        children.put(ConfigurationSchemaNode.OA_PARAMS, new BooleanCheckerParamsType(params));
        return new BooleanCheckerType(children);
    }
}