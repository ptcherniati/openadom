package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import org.apache.commons.collections4.MapUtils;

import java.util.HashMap;
import java.util.Map;

public sealed interface CheckerType<T> extends ApplicationType<T>
        permits BooleanCheckerType, CheckerFactory, DateCheckerType, FloatCheckerType, GroovyCheckerType, IntegerCheckerType, ReferenceCheckerType, StringCheckerType {

    static CheckerType<?> EMPTY_INSTANCE() {
        return new CheckerFactory();
    }

    default Map<String, ConfigurationSchemaNodeType<?>> addNameNode(Map<String, ConfigurationSchemaNodeType<?>> children) {
        Map<String, ConfigurationSchemaNodeType<?>> children1 = new HashMap<>(children);
        children1.put(
                ConfigurationSchemaNode.OA_NAME,
                new EnumType(
                        getChecker().name(),
                        CheckerEnum.VALUES
                )
        );
        return MapUtils.unmodifiableMap(children1);
    }

    CheckerEnum getChecker();
}