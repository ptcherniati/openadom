package fr.inra.oresing.domain.application.configuration.type;

import java.util.*;
import java.util.stream.Collectors;

public enum CheckerEnum implements Comparable<CheckerEnum> {
    OA_reference("OA_reference"),
    OA_boolean("OA_boolean"),
    OA_date("OA_date"),
    OA_integer("OA_integer"),
    OA_float("OA_float"),
    OA_string("OA_string"),
    OA_groovyExpression("OA_groovyExpression");
    public static final Set<String> VALUES = Arrays.stream(values()).map(CheckerEnum::getName).collect(Collectors.toSet());

    private final String name;

    CheckerEnum(final String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}