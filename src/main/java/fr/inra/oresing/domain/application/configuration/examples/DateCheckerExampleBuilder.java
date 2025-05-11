package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class DateCheckerExampleBuilder {
    protected static final DateCheckerType DDMMYYYY = buildDateChecker("dd/MM/yyyy", "01/01/2013", "31/12/2013", "1 Day", Multiplicity.ONE);
    protected static final DateCheckerType DDMMYYYY2 = buildDateChecker("dd/MM/yyyy", "01/01/2004", null, "1 Day", Multiplicity.ONE);
    protected static final DateCheckerType HHMMSS = buildDateChecker("HH:mm:ss", "08:00:00", "17:00:00", null, Multiplicity.ONE);
    protected static final DateCheckerType DDMMYYYYHHMMSS = buildDateChecker("dd/MM/yyyy HH:mm:ss", null, null, null, Multiplicity.ONE);

    protected static DateCheckerType buildDateChecker(final String pattern, final String min, final String max, final String duration, final Multiplicity multiplicity) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<>();
        final HashMap<String, ConfigurationSchemaNodeType> params = new HashMap<>();
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        Optional.ofNullable(pattern)
                .map(p -> new StringType(p, true))
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_PATTERN, m));
        Optional.ofNullable(min)
                .map(StringType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_MIN, m));
        Optional.ofNullable(max)
                .map(StringType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_MAX, m));
        Optional.ofNullable(duration)
                .map(StringType::new)
                .ifPresent(m -> params.put(ConfigurationSchemaNode.OA_DURATION, m));
        children.put(ConfigurationSchemaNode.OA_PARAMS, new DateCheckerParamsType(params));
        return new DateCheckerType(children);

    }
}