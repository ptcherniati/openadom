package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;

import java.util.Map;

public sealed interface IntermediaryType<T>
        extends ConfigurationSchemaNodeType<T>
        permits ApplicationType, CollectionType, IntermediaryType.CheckerParamType {

    default String buildExample(final int level) {
        final StringBuilder builder = getBuilder();
        for (final Map.Entry<String, ConfigurationSchemaNodeType<?>> entry : ((Map<String, ConfigurationSchemaNodeType<?>>) children()).entrySet()) {
            final String label = entry.getKey();
            final ConfigurationSchemaNodeType<?> value = entry.getValue();
            if (value == null) {
                continue;
            }
            builder.append(" \n%1$s%2$s: %3$s\n".formatted(Strings.repeat("  ", level), label, value.buildExample(level + 1)));
        }
        return builder.toString()
                .replaceAll("^\\s#.*\\n", "");
    }

    sealed interface CheckerParamType extends IntermediaryType<Map<String, ConfigurationSchemaNodeType<?>>> permits BooleanCheckerParamsType, DateCheckerParamsType, DefaultValueType, FloatCheckerParamsType, GroovyCheckerParamsType, GroovyExpressionType, IntegerCheckerParamsType, ReferenceCheckerParamsType, StringCheckerParamsType {

    }
}