package fr.inra.oresing.checker;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum CheckerType {
    Reference("Reference"),
    Date("Date"),
    Integer("Integer"),
    Float("Float"),
    RegularExpression("RegularExpression"),
    GroovyExpression("GroovyExpression");

    public static SiOreIllegalArgumentException getError(CheckerType checkerType) {
        return new SiOreIllegalArgumentException(
                "badCheckerType",
                Map.of(
                        "checkerType", checkerType,
                        "knownCheckerType", Arrays.stream(CheckerType.values()).map(CheckerType::toString).collect(Collectors.toSet())
                )
        );
    }
    public static SiOreIllegalArgumentException getError(CheckerType checkerType, Set<CheckerType> knownCheckerType) {
        return new SiOreIllegalArgumentException(
                "badCheckerType",
                Map.of(
                        "checkerType", checkerType,
                        "knownCheckerType", knownCheckerType
                )
        );
    }

    public final String getName() {
        return name;
    }

    private final String name;

    CheckerType(String name) {
        this.name= name;
    }

    @Override
    public String toString() {
        return name;
    }
}