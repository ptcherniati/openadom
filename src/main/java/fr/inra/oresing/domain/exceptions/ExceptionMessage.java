package fr.inra.oresing.domain.exceptions;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Codes d'erreur métier utilisés dans les exceptions du domaine et de la persistence.
 * Déplacé de {@code rest.exceptions} vers {@code domain.exceptions} (Phase 1 — indépendance domaine).
 */
public enum ExceptionMessage {
    NULL_LABEL,
    TOO_LONG_LABEL,
    INAPPROPRIATE_LABEL,
    MISSING_PRIMARY_KEY_COMPONENT,
    SEPARATOR_USING_IN_VALUE,
    MISSING_COLUMN,
    NULL_NATURAL_KEY,
    IO_EXCEPTION,
    JSON_PROCESSING,
    JSON_EXCEPTION,
    SQL_EXCEPTION,
    SYSTEM_USER_NOT_CONNECTED,
    BAD_METHOD;

    public String toMessage() {
        final String message = Arrays.stream(name().split("_"))
                .map(n -> n.charAt(0) + n.substring(1).toLowerCase())
                .collect(Collectors.joining());
        return message.replaceFirst("^.", message.substring(0, 1).toLowerCase());
    }
}