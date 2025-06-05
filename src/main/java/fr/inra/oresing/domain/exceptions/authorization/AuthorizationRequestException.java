package fr.inra.oresing.domain.exceptions.authorization;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public enum AuthorizationRequestException {
    NO_AUTHORIZATION_NAME,
    BAD_REFERENCES,

    BAD_FILE_NAME_START_DATE,
    BAD_FILE_NAME_END_DATE,
    INVALID_FILE_NAME,

    MISSING_REQUIRED_AUTHORIZATION;


    private final String message;

    AuthorizationRequestException() {
        message = toMessage(name());
    }

    private static String toMessage(final String name) {
        final String message = Arrays.stream(name.split("_"))
                .map(n -> n.charAt(0) + n.substring(1).toLowerCase())
                .collect(Collectors.joining());
        return message.replaceFirst("^.", message.substring(0, 1).toLowerCase());
    }
}