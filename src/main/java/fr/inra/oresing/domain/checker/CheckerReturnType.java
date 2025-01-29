package fr.inra.oresing.domain.checker;

import fr.inra.oresing.domain.groovy.GroovyExpression;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;

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

    public static SiOreIllegalArgumentException getError(final Object evaluation, final GroovyExpression expression, final Map<String, Object> context) {
        return new SiOreIllegalArgumentException(
                "badGroovyExpressionCheckerReturnType",
                Map.of(
                        "value", evaluation,
                        "expression", expression.toString(),
                        "context", context,
                        "knownCheckerReturnType", Arrays.stream(values()).map(CheckerReturnType::toString).collect(Collectors.toSet())
                )
        );
    }

    public static SiOreIllegalArgumentException getError(final Object evaluation, final GroovyExpression expression, final Map<String, Object> context, final Set<CheckerReturnType> knownCheckerReturnType) {
        return new SiOreIllegalArgumentException(
                "badGroovyExpressionCheckerReturnFalse",
                Map.of(
                        "value", evaluation,
                        "expression", expression.toString(),
                        "context", context,
                        "knownCheckerReturnType", knownCheckerReturnType
                )
        );
    }

    public final String getName() {
        return name;
    }

    private final String name;

    CheckerReturnType(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}