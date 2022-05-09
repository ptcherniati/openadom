package fr.inra.oresing.checker;

import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum CheckerReturnType {
    BOOLEAN("Boolean"),
    STRING("String"),
    NUMBER("Number"),
    SET_OF_STRING("Set<String>"),
    SET_OF_NUMBER("Set<Number>");

    public static SiOreIllegalArgumentException getError(Object evaluation, GroovyExpression expression, Map<String, Object> context) {
        return new SiOreIllegalArgumentException(
                "badGroovyExpressionCheckerReturnType",
                Map.of(
                        "value", evaluation,
                        "expression", expression,
                        "context", context,
                        "knownCheckerReturnType", Arrays.stream(CheckerReturnType.values()).map(CheckerReturnType::toString).collect(Collectors.toSet())
                )
        );
    }
    public static SiOreIllegalArgumentException getError(Object evaluation, GroovyExpression expression, Map<String, Object> context, Set<CheckerReturnType> knownCheckerType) {
        return new SiOreIllegalArgumentException(
                "badGroovyExpressionCheckerReturnType",
                Map.of(
                        "value", evaluation,
                        "expression", expression,
                        "context", context,
                        "knownCheckerType", knownCheckerType
                )
        );
    }

    public final String getName() {
        return name;
    }

    private final String name;

    CheckerReturnType(String name) {
        this.name= name;
    }

    @Override
    public String toString() {
        return name;
    }
}