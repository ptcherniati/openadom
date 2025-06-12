package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.EnumType;
import fr.inra.oresing.domain.application.configuration.type.ReferenceCheckerParamsType;
import fr.inra.oresing.domain.application.configuration.type.ReferenceCheckerType;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.Map;

class ReferenceCheckerExampleBuilder {
    protected static final ReferenceCheckerType SITE = buildReferenceChecker("tr_zone_etude_zet", Multiplicity.ONE, false, true);
    protected static final ReferenceCheckerType TYPE_DE_SITES = buildReferenceChecker("tr_type_zone_etude_tze", Multiplicity.ONE, true, false);

    protected static ReferenceCheckerType buildReferenceChecker(final String reference, final Multiplicity multiplicity, final boolean isParent, final boolean isRecursive) {
        final Map<String, ConfigurationSchemaNodeType<?>> children = new HashMap<>();
        final HashMap<String, ConfigurationSchemaNodeType<?>> params = new HashMap<>();
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        params.put(ConfigurationSchemaNode.OA_REFERENCE, ReferenceExampleBuilder.buildReference(reference, isParent, isRecursive));
        children.put(ConfigurationSchemaNode.OA_PARAMS, new ReferenceCheckerParamsType(params));
        return new ReferenceCheckerType(children);
    }
}