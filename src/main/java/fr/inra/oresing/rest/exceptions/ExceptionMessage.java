package fr.inra.oresing.rest.exceptions;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum ExceptionMessage {
    NULL_LABEL,
    TOO_LONG_LABEL,
    INAPPROPRIATE_LABEL,
    MISSING_PRIMARY_KEY_COMPONENT,
    SEPARATOR_USING_IN_VALUE,
    MISSING_COLUMN,
    NULL_NATURAL_KEY;

    public String toMessage() {
        final String message = Arrays.stream(name().split("_"))
                .map(n -> n.substring(0, 1) + n.substring(1, n.length()).toLowerCase())
                .collect(Collectors.joining());
        return message.replaceFirst("^.", message.substring(0, 1).toLowerCase());
    }
}
